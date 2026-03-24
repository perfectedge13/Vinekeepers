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
import com.vinekeepers.workflow.planning.PlanningMaterialSpreadKeys;
import com.vinekeepers.workflow.planreview.PlanningArtifactTexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional OpenAI-backed pass: merges structured upserts into plan artifacts from draft-only JSON. Safe no-op when API
 * is unavailable; failures are visible via spread keys (never a silent hang). Routing is decided only by the evaluation
 * pass, not by synthesis output.
 */
public final class RunLlmPlanningSynthesisAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final Logger log = LoggerFactory.getLogger(RunLlmPlanningSynthesisAction.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SYSTEM = """
            You are a planning assistant for software feature intake. Reply with a single JSON object only, no markdown fences.
            Contract rules: use real artifactId/sectionId pairs from the profile, use real field ids inside data,
            never use the literal key "fieldId", and never put a field id in sectionId.
            This pass produces draft content only; do not emit fields that choose the next workflow step or route.
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
              "implementation_scope_notes": "string or empty — draft scope notes only",
              "explicit_assumptions": ["short strings"],
              "draft_question_candidate": "optional string — at most one draft clarification phrasing; empty if none; does not control routing"
            }
            Use only artifact/section ids that exist in the profile snapshot. Prefer enriching current_state_summary,
            feature_summary, scope_summary, user_stories, and acceptance_criteria. Keep values concise.
            Return exactly one JSON object as the full response body. Do not add prefatory text, explanations, or trailing notes.
            If you are unsure, prefer {"upserts":[],"explicit_assumptions":[],"implementation_scope_notes":"",
            "top_unresolved_gap":"","draft_question_candidate":"","repo_evidence_this_pass":"not_inspected"}
            over malformed JSON or placeholder keys.
            Wrong: {"artifactId":"requirements_spec","sectionId":"feature_summary","data":{"current_state_summary":"x"}}
            Right: {"artifactId":"requirements_spec","sectionId":"narrative","data":{"feature_summary":"x","current_state_summary":"y"}}
            Separate observed repo facts this pass from inference and unknowns; do not name paths/packages unless observed.
            If nothing should change, return {"upserts":[],"explicit_assumptions":[],"implementation_scope_notes":"",
            "top_unresolved_gap":"","draft_question_candidate":"","repo_evidence_this_pass":"not_inspected"}.
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
        spread.put("planningLlmUpsertCount", "0");
        spread.put("planningSynthesisParseOk", "false");
        spread.put("planningSynthesisRepairAttempted", "false");
        spread.put("planningSynthesisRepairExhausted", "false");
        spread.put("planningSynthesisFailureCategory", "");
        spread.put("planningSynthesisUpsertsAttempted", "0");
        spread.put("planningSynthesisUpsertsRejected", "0");
        spread.put(PlanningMaterialSpreadKeys.SYNTHESIS_DRAFT_QUESTION_CANDIDATE_KEY, "");
        spread.put(PlanningMaterialSpreadKeys.SYNTHESIS_TOP_UNRESOLVED_GAP_KEY, "");
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
                            "Updating the plan draft.");
            raw = openAiChatClient.complete(SYSTEM, userPayload, model, timeoutMs, callCtx);
        } catch (Exception e) {
            spread.put("planningSynthesisFailureCategory", PlanningFailureCategory.SYNTHESIS_TRANSPORT_ERROR.name());
            spread.put("planningLlmError", e.getMessage() != null ? e.getMessage() : "synthesis failed");
            log.warn("Planning LLM call failed: {}", spread.get("planningLlmError"));
            return spread;
        }
        if (raw.startsWith("ERROR:")) {
            spread.put("planningSynthesisFailureCategory", PlanningFailureCategory.SYNTHESIS_TRANSPORT_ERROR.name());
            spread.put("planningLlmError", raw);
            log.warn("Planning LLM: {}", raw);
            return spread;
        }
        PlanningLlmJsonSupport.ParsedJsonObjectResult parsed =
                PlanningLlmJsonSupport.parseJsonObjectWithRepair(openAiChatClient, raw, "planning synthesis", event, state);
        spread.put("planningSynthesisRepairAttempted", parsed.repairAttempted() ? "true" : "false");
        spread.put("planningSynthesisRepairExhausted", parsed.repairExhausted() ? "true" : "false");
        if (!parsed.success()) {
            spread.put(
                    "planningSynthesisFailureCategory",
                    parsed.repairAttempted()
                            ? PlanningFailureCategory.SYNTHESIS_REPAIR_EXHAUSTED.name()
                            : PlanningFailureCategory.SYNTHESIS_JSON_INVALID.name());
            spread.put("planningLlmError", parsed.errorMessage());
            log.warn("Planning LLM parse/apply failed: {}", spread.get("planningLlmError"));
            return spread;
        }
        try {
            JsonNode synthRoot = parsed.root();
            applyStructuredResponse(event, state, contextId, spread, synthRoot);
            spread.put(
                    PlanningMaterialSpreadKeys.SYNTHESIS_DRAFT_QUESTION_CANDIDATE_KEY,
                    PlanningLlmJsonSupport.readDraftQuestionCandidate(synthRoot));
            spread.put(
                    PlanningMaterialSpreadKeys.SYNTHESIS_TOP_UNRESOLVED_GAP_KEY,
                    PlanningLlmJsonSupport.readTopUnresolvedGap(synthRoot));
            spread.put("planningSynthesisParseOk", "true");
        } catch (Exception e) {
            spread.put("planningSynthesisFailureCategory", PlanningFailureCategory.SYNTHESIS_UPSERT_REJECTED.name());
            spread.put("planningLlmError", e.getMessage() != null ? e.getMessage() : "apply failed");
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
            return;
        }
        spread.put("planningLlmUpsertCount", Integer.toString(upsertResult.applied()));
        spread.put("planningLlmOk", "true");
        spread.put("planningLlmError", "");
        spread.put(
                "planningSynthesisFailureCategory",
                upsertResult.attempted() == 0
                                && upsertResult.applied() == 0
                                && !PlanningLlmJsonSupport.hasMeaningfulPlanningContent(root)
                        ? PlanningFailureCategory.SYNTHESIS_EMPTY_NOOP.name()
                        : "");
    }

    private static String buildUserPayload(FeaturePlanState plan, String profileId, Map<String, Object> state) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("profileId", profileId);
        snap.put("initialRequest", plan.getInitialRequest());
        snap.put("repoRef", plan.getRepoRef());
        snap.put(
                "section_id_examples",
                Map.of(
                        "requirements_spec", "narrative",
                        "overall_plan", "outline",
                        "request_exploration", "analysis"));
        snap.put(
                "field_id_note",
                "feature_summary, current_state_summary, and scope_summary are field ids inside data, not sectionId values.");
        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put(
                "request_exploration",
                Map.of(
                        "analysis",
                        sectionData(
                                "exploration_body",
                                PlanningArtifactTexts.artifactField(
                                        plan, "request_exploration", "analysis", "exploration_body"))));
        artifacts.put(
                "requirements_spec",
                Map.of(
                        "narrative",
                        sectionData(
                                "feature_summary",
                                PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "feature_summary"),
                                "current_state_summary",
                                PlanningArtifactTexts.artifactField(
                                        plan, "requirements_spec", "narrative", "current_state_summary"),
                                "scope_summary",
                                PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "scope_summary"),
                                "user_stories",
                                PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "user_stories"),
                                "acceptance_criteria",
                                PlanningArtifactTexts.artifactField(
                                        plan, "requirements_spec", "narrative", "acceptance_criteria"))));
        artifacts.put(
                "overall_plan",
                Map.of(
                        "outline",
                        sectionData(
                                "plan_body",
                                PlanningArtifactTexts.artifactField(plan, "overall_plan", "outline", "plan_body"))));
        snap.put("artifacts", artifacts);
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

    private static Map<String, Object> sectionData(Object... keyValues) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object key = keyValues[i];
            if (key != null) {
                out.put(key.toString(), keyValues[i + 1]);
            }
        }
        return out;
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
