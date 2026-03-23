package com.vinekeepers.workflow.actions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.connectors.openai.OpenAiCallContext;
import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.PlanningFailureCategory;
import com.vinekeepers.workflow.planning.PlanningLlmJsonSupport;
import com.vinekeepers.workflow.planreview.PlanningArtifactTexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional OpenAI-backed pass: merges structured upserts into plan artifacts and records follow-up questions for
 * discovery. Safe no-op when API is unavailable; failures are visible via spread keys (never a silent hang).
 */
public final class RunLlmPlanningSynthesisAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final Logger log = LoggerFactory.getLogger(RunLlmPlanningSynthesisAction.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SYSTEM = """
            You are a planning assistant for software feature intake. Reply with a single JSON object only, no markdown fences.
            Contract rules: use real artifactId/sectionId pairs from the profile, use real field ids inside data,
            never use the literal key "fieldId", and never put a field id in sectionId.
            Schema:
            {
              "repo_evidence_this_pass": "observed | inferred_unverified | not_inspected",
              "upserts": [
                {
                  "artifactId": "requirements_spec",
                  "sectionId": "narrative",
                  "mode": "replace",
                  "data": { "current_state_summary": "value string" }
                }
              ],
              "top_unresolved_gap": "string or empty",
              "recommended_action": "ASK_ONE_QUESTION | ASSUME_AND_CONTINUE | POST_PACKET | BLOCK",
              "question_if_needed": "single string — empty unless recommended_action is ASK_ONE_QUESTION",
              "explicit_assumptions": ["short strings"]
            }
            If the model still emits a legacy follow_up_questions array, it is ignored except for backward compatibility.
            Always leave follow_up_questions empty when it is present.
            Always put any single clarification in question_if_needed only.
            Use only artifact/section ids that exist in the profile snapshot. Prefer enriching current_state_summary,
            feature_summary, scope_summary, user_stories, acceptance_criteria, open_questions. Keep values concise.
            Separate observed repo facts this pass from inference and unknowns; do not name paths/packages unless observed.
            At most one clarification question per response, only in question_if_needed.
            If nothing should change, return {"upserts":[],"follow_up_questions":[],"explicit_assumptions":[],
            "question_if_needed":"","top_unresolved_gap":"","recommended_action":"POST_PACKET","repo_evidence_this_pass":"not_inspected"}.
            """;

    private final OpenAiChatClient openAiChatClient;
    private final FeaturePlanStateStore planStateStore;
    private final WorkProfileRegistry workProfileRegistry;

    public RunLlmPlanningSynthesisAction(
            OpenAiChatClient openAiChatClient,
            FeaturePlanStateStore planStateStore,
            WorkProfileRegistry workProfileRegistry) {
        this.openAiChatClient = openAiChatClient;
        this.planStateStore = planStateStore;
        this.workProfileRegistry = workProfileRegistry;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("planningLlmOk", "false");
        spread.put("planningLlmError", "");
        spread.put("planningLlmSkipReason", "");
        spread.put("planningFollowUpQuestionsJson", "[]");
        spread.put("planningLlmUpsertCount", "0");
        spread.put("planningSynthesisParseOk", "false");
        spread.put("planningSynthesisRepairAttempted", "false");
        spread.put("planningSynthesisRepairExhausted", "false");
        spread.put("planningSynthesisFailureCategory", "");
        spread.put("planningSynthesisUpsertsAttempted", "0");
        spread.put("planningSynthesisUpsertsRejected", "0");
        if (planStateStore == null || workProfileRegistry == null) {
            spread.put("planningLlmSkipReason", "MISSING_DEPS");
            return spread;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            spread.put("planningLlmSkipReason", "NO_CONTEXT");
            return spread;
        }
        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            spread.put("planningLlmSkipReason", "NO_PLAN");
            return spread;
        }
        String profileId = plan.getProfileId();
        if (profileId == null || profileId.isBlank()) {
            spread.put("planningLlmSkipReason", "NO_PROFILE");
            return spread;
        }
        WorkProfileDefinition profile = workProfileRegistry.get(profileId).orElse(null);
        if (profile == null || profile.findSection("request_exploration", "analysis").isEmpty()) {
            spread.put("planningLlmSkipReason", "PROFILE_NOT_V2");
            return spread;
        }
        if (openAiChatClient == null || !openAiChatClient.isConfigured()) {
            spread.put("planningLlmSkipReason", "NO_API_KEY");
            spread.put("planningLlmOk", "false");
            return spread;
        }

        String userPayload = buildUserPayload(plan, profileId, state);
        String model = firstNonBlank(getString(bind, "llmModel"), getString(state, "workflowLlmModel"));
        Long timeoutMs = parseTimeoutMs(firstNonBlank(getString(bind, "llmTimeoutMs"), getString(state, "workflowLlmTimeoutMs")));
        String raw;
        try {
            OpenAiCallContext callCtx =
                    OpenAiCallContext.planning(
                            event,
                            state,
                            "Synthesizing the draft plan and follow-up questions (asking ChatGPT).");
            raw = openAiChatClient.complete(SYSTEM, userPayload, model, timeoutMs, callCtx);
        } catch (Exception e) {
            spread.put("planningLlmError", e.getMessage() != null ? e.getMessage() : "synthesis failed");
            log.warn("Planning LLM call failed: {}", spread.get("planningLlmError"));
            return spread;
        }
        if (raw.startsWith("ERROR:")) {
            spread.put("planningLlmError", raw);
            log.warn("Planning LLM: {}", raw);
            return spread;
        }
        try {
            applyStructuredResponse(
                    event,
                    state,
                    contextId,
                    spread,
                    PlanningLlmJsonSupport.parseJsonObject(raw));
            spread.put("planningSynthesisParseOk", "true");
        } catch (Exception e) {
            Exception parseFailure = e;
            spread.put("planningSynthesisFailureCategory", PlanningFailureCategory.SYNTHESIS_JSON_INVALID.name());
            String repaired = PlanningLlmJsonSupport.tryRepairJson(openAiChatClient, raw, "planning synthesis", event, state);
            if (repaired != null) {
                spread.put("planningSynthesisRepairAttempted", "true");
                try {
                    applyStructuredResponse(
                            event,
                            state,
                            contextId,
                            spread,
                            PlanningLlmJsonSupport.parseJsonObject(repaired));
                    spread.put("planningSynthesisParseOk", "true");
                    spread.put("planningSynthesisFailureCategory", "");
                    return spread;
                } catch (Exception repairedFailure) {
                    parseFailure = repairedFailure;
                    spread.put("planningSynthesisRepairExhausted", "true");
                    spread.put("planningSynthesisFailureCategory", PlanningFailureCategory.SYNTHESIS_REPAIR_EXHAUSTED.name());
                    log.warn("Planning LLM parse failed after repair: {}", repairedFailure.getMessage());
                }
            } else {
                spread.put("planningSynthesisRepairExhausted", "true");
                spread.put("planningSynthesisFailureCategory", PlanningFailureCategory.SYNTHESIS_REPAIR_EXHAUSTED.name());
            }
            spread.put("planningLlmError", parseFailure.getMessage() != null ? parseFailure.getMessage() : "parse failed");
            log.warn("Planning LLM parse/apply failed: {}", spread.get("planningLlmError"));
        }
        return spread;
    }

    static String extractJsonObject(String raw) {
        return PlanningLlmJsonSupport.extractJsonObject(raw);
    }

    private void applyStructuredResponse(
            Event event, Map<String, Object> state, String contextId, Map<String, Object> spread, JsonNode root)
            throws Exception {
        PlanningLlmJsonSupport.UpsertApplyResult upsertResult =
                PlanningLlmJsonSupport.applyUpsertsDetailed(
                        root, event, state, contextId, planStateStore, workProfileRegistry);
        spread.put("planningSynthesisUpsertsAttempted", Integer.toString(upsertResult.attempted()));
        spread.put("planningSynthesisUpsertsRejected", Integer.toString(upsertResult.rejected().size()));
        if (upsertResult.attempted() > 0 && upsertResult.applied() == 0) {
            spread.put("planningSynthesisFailureCategory", PlanningFailureCategory.SYNTHESIS_UPSERT_REJECTED.name());
            spread.put("planningLlmOk", "false");
            spread.put("planningLlmError", PlanningLlmJsonSupport.summarizeRejectedUpserts(upsertResult));
            spread.put("planningLlmUpsertCount", "0");
            spread.put("planningFollowUpQuestionsJson", "[]");
            return;
        }
        List<String> followUps = new ArrayList<>(PlanningLlmJsonSupport.readFollowUpQuestions(root));
        String qNeeded = root.path("question_if_needed").asText("").trim();
        if (followUps.isEmpty() && !qNeeded.isBlank()) {
            followUps.add(qNeeded);
        } else if (followUps.size() > 1) {
            followUps = new ArrayList<>(followUps.subList(0, 1));
        }
        spread.put("planningLlmUpsertCount", Integer.toString(upsertResult.applied()));
        spread.put("planningFollowUpQuestionsJson", JSON.writeValueAsString(followUps));
        spread.put("planningLlmOk", "true");
        spread.put("planningLlmError", "");
        spread.put("planningSynthesisFailureCategory", "");
    }

    private static String buildUserPayload(FeaturePlanState plan, String profileId, Map<String, Object> state) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("profileId", profileId);
        snap.put("initialRequest", plan.getInitialRequest());
        snap.put("repoRef", plan.getRepoRef());
        snap.put("exploration", PlanningArtifactTexts.artifactField(plan, "request_exploration", "analysis", "exploration_body"));
        snap.put("feature_summary", PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "feature_summary"));
        snap.put("current_state_summary", PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "current_state_summary"));
        snap.put("scope_summary", PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "scope_summary"));
        snap.put("user_stories", PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "user_stories"));
        snap.put("acceptance_criteria", PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "acceptance_criteria"));
        snap.put("open_questions", PlanningArtifactTexts.artifactField(plan, "open_questions_block", "backlog", "open_questions"));
        snap.put("plan_body", PlanningArtifactTexts.artifactField(plan, "overall_plan", "outline", "plan_body"));
        if (state != null && "true".equalsIgnoreCase(String.valueOf(state.get("planningRagAvailable")))) {
            String rag = state.get("planningRagRetrievalText") != null ? state.get("planningRagRetrievalText").toString() : "";
            if (rag != null && !rag.isBlank()) {
                snap.put("repo_grounding_snippets", rag);
            }
        }
        try {
            return "Profile snapshot (JSON):\n" + JSON.writerWithDefaultPrettyPrinter().writeValueAsString(snap);
        } catch (JsonProcessingException e) {
            log.warn("Planning LLM: failed to serialize profile snapshot: {}", e.getMessage());
            return "Profile snapshot (JSON):\n{}";
        }
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }

    private static Long parseTimeoutMs(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long v = Long.parseLong(raw.trim());
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
