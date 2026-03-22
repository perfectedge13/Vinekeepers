package com.vinekeepers.workflow.actions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
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
            Schema:
            {
              "upserts": [
                {
                  "artifactId": "requirements_spec",
                  "sectionId": "narrative",
                  "mode": "replace",
                  "data": { "fieldId": "value string" }
                }
              ],
              "follow_up_questions": [ "short question?" ]
            }
            Use only artifact/section ids that exist in the profile snapshot. Prefer enriching current_state_summary,
            feature_summary, scope_summary, user_stories, acceptance_criteria, open_questions. Keep values concise.
            If nothing should change, return {"upserts":[],"follow_up_questions":[]}.
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

        String userPayload = buildUserPayload(plan, profileId);
        String model = firstNonBlank(getString(bind, "llmModel"), getString(state, "workflowLlmModel"));
        Long timeoutMs = parseTimeoutMs(firstNonBlank(getString(bind, "llmTimeoutMs"), getString(state, "workflowLlmTimeoutMs")));
        String raw;
        try {
            raw = openAiChatClient.complete(SYSTEM, userPayload, model, timeoutMs);
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
            String json = extractJsonObject(raw);
            JsonNode root = JSON.readTree(json);
            JsonNode upsertsNode = root.path("upserts");
            List<Map<String, Object>> upserts = new ArrayList<>();
            if (upsertsNode.isArray()) {
                for (JsonNode n : upsertsNode) {
                    if (n.isObject()) {
                        upserts.add(JSON.convertValue(n, new TypeReference<>() {}));
                    }
                }
            }
            List<String> followUps = new ArrayList<>();
            JsonNode fq = root.path("follow_up_questions");
            if (fq.isArray()) {
                for (JsonNode n : fq) {
                    if (n.isTextual()) {
                        String q = n.asText().trim();
                        if (!q.isBlank()) {
                            followUps.add(q);
                        }
                    }
                }
            }
            UpsertArtifactSectionDataAction upsertAction = new UpsertArtifactSectionDataAction(planStateStore, workProfileRegistry);
            Map<String, Object> base = new LinkedHashMap<>();
            if (state != null) {
                base.putAll(state);
            }
            base.put("contextId", contextId);
            int applied = 0;
            for (Map<String, Object> u : upserts) {
                Object aid = u.get("artifactId");
                Object sid = u.get("sectionId");
                if (aid == null || sid == null) {
                    continue;
                }
                Object dataObj = u.get("data");
                if (!(dataObj instanceof Map<?, ?>)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> dataMap = (Map<String, Object>) dataObj;
                String mode = u.get("mode") != null ? u.get("mode").toString() : "replace";
                Object res = upsertAction.run(
                        event,
                        base,
                        Map.of(
                                "artifactId",
                                aid.toString(),
                                "sectionId",
                                sid.toString(),
                                "mode",
                                mode,
                                "data",
                                dataMap));
                if ("OK".equals(res)) {
                    applied++;
                }
            }
            spread.put("planningLlmUpsertCount", Integer.toString(applied));
            spread.put("planningFollowUpQuestionsJson", JSON.writeValueAsString(followUps));
            spread.put("planningLlmOk", "true");
            spread.put("planningLlmError", "");
        } catch (Exception e) {
            spread.put("planningLlmError", e.getMessage() != null ? e.getMessage() : "parse failed");
            log.warn("Planning LLM parse/apply failed: {}", spread.get("planningLlmError"));
        }
        return spread;
    }

    static String extractJsonObject(String raw) {
        if (raw == null) {
            return "{}";
        }
        String t = raw.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) {
                t = t.substring(nl + 1);
            }
            int end = t.lastIndexOf("```");
            if (end > 0) {
                t = t.substring(0, end).trim();
            }
        }
        int start = t.indexOf('{');
        int last = t.lastIndexOf('}');
        if (start >= 0 && last > start) {
            return t.substring(start, last + 1);
        }
        return t;
    }

    private static String buildUserPayload(FeaturePlanState plan, String profileId) {
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
