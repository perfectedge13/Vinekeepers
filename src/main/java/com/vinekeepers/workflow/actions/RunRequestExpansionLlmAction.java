package com.vinekeepers.workflow.actions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.planning.OpenAiPlanningContentGenerator;
import com.vinekeepers.workflow.planning.PlanningContentGenerator;
import com.vinekeepers.workflow.planreview.PlanningArtifactTexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request-expansion LLM phase: structured JSON merged into artifacts before deterministic exploration build.
 */
public final class RunRequestExpansionLlmAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final Logger log = LoggerFactory.getLogger(RunRequestExpansionLlmAction.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SYSTEM = """
            You are a planning assistant. Reply with a single JSON object only, no markdown fences.
            Schema:
            {
              "current_state": "string",
              "proposed_behavior": "string",
              "config_model": "string",
              "selection_granularity": "string",
              "change_type": "string",
              "compatibility_fallback": "string",
              "impacted_components": "string (concrete paths, packages, or file patterns when repo context exists)",
              "candidate_open_questions": ["short question?"],
              "validation_concerns": "string",
              "design_options": "optional string",
              "follow_up_decisions": [ { "id": "d1", "question": "text", "choice_a": "a", "choice_b": "b", "choice_c": "optional" } ],
              "upserts": [ { "artifactId": "requirements_spec", "sectionId": "narrative", "mode": "replace", "data": { "fieldId": "value" } } ]
            }
            Populate fields from the user's feature request and repo snapshot. Avoid echoing the request verbatim as the only content.
            Use impacted_components with real code-ish tokens (e.g. src/, .java, package segments) when a repo path is provided.
            If unsure, still propose concrete verification steps in validation_concerns referencing the request themes.
            """;

    private final PlanningContentGenerator generator;
    private final OpenAiChatClient openAiChatClient;
    private final FeaturePlanStateStore planStateStore;
    private final WorkProfileRegistry workProfileRegistry;

    public RunRequestExpansionLlmAction(
            OpenAiChatClient openAiChatClient,
            FeaturePlanStateStore planStateStore,
            WorkProfileRegistry workProfileRegistry) {
        this(OpenAiPlanningContentGenerator.INSTANCE, openAiChatClient, planStateStore, workProfileRegistry);
    }

    public RunRequestExpansionLlmAction(
            PlanningContentGenerator generator,
            OpenAiChatClient openAiChatClient,
            FeaturePlanStateStore planStateStore,
            WorkProfileRegistry workProfileRegistry) {
        this.generator = generator != null ? generator : OpenAiPlanningContentGenerator.INSTANCE;
        this.openAiChatClient = openAiChatClient;
        this.planStateStore = planStateStore;
        this.workProfileRegistry = workProfileRegistry;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = baseSpread();
        if (planStateStore == null || workProfileRegistry == null) {
            spread.put("planningExpansionSkipReason", "MISSING_DEPS");
            spread.put("planningExpansionFallbackUsed", "true");
            return spread;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            spread.put("planningExpansionSkipReason", "NO_CONTEXT");
            spread.put("planningExpansionFallbackUsed", "true");
            return spread;
        }
        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            spread.put("planningExpansionSkipReason", "NO_PLAN");
            spread.put("planningExpansionFallbackUsed", "true");
            return spread;
        }
        String profileId = plan.getProfileId();
        WorkProfileDefinition profile =
                profileId != null && !profileId.isBlank() ? workProfileRegistry.get(profileId).orElse(null) : null;
        if (profile == null || profile.findSection("request_exploration", "analysis").isEmpty()) {
            spread.put("planningExpansionSkipReason", "PROFILE_NOT_V2");
            spread.put("planningExpansionFallbackUsed", "true");
            return spread;
        }
        if (openAiChatClient == null || !openAiChatClient.isConfigured()) {
            spread.put("planningExpansionSkipReason", "NO_API_KEY");
            spread.put("planningExpansionFallbackUsed", "true");
            spread.put("planningLlmOk", "false");
            return spread;
        }

        String userPayload = buildUserPayload(plan, profileId);
        String model = firstNonBlank(getString(bind, "llmModel"), getString(state, "workflowLlmModel"));
        Long timeout = parseTimeoutMs(firstNonBlank(getString(bind, "llmTimeoutMs"), getString(state, "workflowLlmTimeoutMs")));

        long t0 = System.currentTimeMillis();
        String raw;
        try {
            raw = generator.complete(openAiChatClient, SYSTEM, userPayload, model, timeout);
        } catch (Exception e) {
            spread.put("planningLlmError", e.getMessage() != null ? e.getMessage() : "expansion failed");
            spread.put("planningExpansionFallbackUsed", "true");
            spread.put("planningExpansionSource", "DETERMINISTIC");
            return spread;
        }
        spread.put("planningExpansionLatencyMs", String.valueOf(System.currentTimeMillis() - t0));
        if (raw.startsWith("ERROR:")) {
            spread.put("planningLlmError", raw);
            spread.put("planningExpansionFallbackUsed", "true");
            spread.put("planningExpansionSource", "DETERMINISTIC");
            spread.put("planningLlmOk", "false");
            return spread;
        }

        try {
            String json = RunLlmPlanningSynthesisAction.extractJsonObject(raw);
            JsonNode root = JSON.readTree(json);
            UpsertArtifactSectionDataAction upsertAction = new UpsertArtifactSectionDataAction(planStateStore, workProfileRegistry);
            Map<String, Object> base = new LinkedHashMap<>();
            if (state != null) {
                base.putAll(state);
            }
            base.put("contextId", contextId);

            String explorationBody = composeExplorationMarkdown(plan, root);
            if (!explorationBody.isBlank()) {
                upsertAction.run(
                        event,
                        base,
                        Map.of(
                                "artifactId",
                                "request_exploration",
                                "sectionId",
                                "analysis",
                                "mode",
                                "replace",
                                "data",
                                Map.of("exploration_body", explorationBody)));
            }

            applyNarrativeField(upsertAction, event, base, root, "current_state", "current_state_summary");
            applyNarrativeField(upsertAction, event, base, root, "proposed_behavior", "feature_summary");
            String scope = text(root, "config_model") + "\n\n" + text(root, "selection_granularity") + "\n\n"
                    + text(root, "change_type") + "\n\n" + text(root, "compatibility_fallback");
            if (scope.trim().length() > 20) {
                upsertAction.run(
                        event,
                        base,
                        Map.of(
                                "artifactId",
                                "requirements_spec",
                                "sectionId",
                                "narrative",
                                "mode",
                                "replace",
                                "data",
                                Map.of("scope_summary", scope.trim())));
            }
            String comps = text(root, "impacted_components");
            if (!comps.isBlank()) {
                upsertAction.run(
                        event,
                        base,
                        Map.of(
                                "artifactId",
                                "architecture_notes",
                                "sectionId",
                                "impact",
                                "mode",
                                "replace",
                                "data",
                                Map.of("components_impacted", comps)));
            }
            String val = text(root, "validation_concerns");
            if (!val.isBlank()) {
                upsertAction.run(
                        event,
                        base,
                        Map.of(
                                "artifactId",
                                "validation_plan",
                                "sectionId",
                                "checks",
                                "mode",
                                "replace",
                                "data",
                                Map.of("validation_notes", val)));
            }
            String design = text(root, "design_options");
            if (!design.isBlank()) {
                upsertAction.run(
                        event,
                        base,
                        Map.of(
                                "artifactId",
                                "overall_plan",
                                "sectionId",
                                "outline",
                                "mode",
                                "replace",
                                "data",
                                Map.of("plan_body", design)));
            }

            int appliedExtras = applyUpsertsArray(upsertAction, event, base, root.path("upserts"));

            List<String> followUps = new ArrayList<>();
            JsonNode fd = root.path("follow_up_decisions");
            if (fd.isArray()) {
                for (JsonNode n : fd) {
                    if (n.isObject()) {
                        String q = n.path("question").asText("").trim();
                        if (!q.isBlank()) {
                            followUps.add(q);
                        }
                    }
                }
            }
            spread.put("planningExpansionFollowUpsJson", JSON.writeValueAsString(followUps));
            spread.put("planningExpansionUpsertExtras", String.valueOf(appliedExtras));
            spread.put("planningExpansionFromLlm", "true");
            spread.put("planningExpansionRichBody", explorationBody.length() >= 200 ? "true" : "false");
            spread.put("planningExpansionSource", "LLM");
            spread.put("planningExpansionFallbackUsed", "false");
            spread.put("planningLlmOk", "true");
            spread.put("planningLlmError", "");
        } catch (Exception e) {
            spread.put("planningLlmError", e.getMessage() != null ? e.getMessage() : "expansion parse failed");
            spread.put("planningExpansionFallbackUsed", "true");
            spread.put("planningExpansionSource", "DETERMINISTIC");
            spread.put("planningLlmOk", "false");
            log.warn("Request expansion parse/apply failed: {}", spread.get("planningLlmError"));
        }
        return spread;
    }

    private static void applyNarrativeField(
            UpsertArtifactSectionDataAction upsert,
            Event event,
            Map<String, Object> base,
            JsonNode root,
            String jsonField,
            String narrativeField) throws JsonProcessingException {
        String v = text(root, jsonField);
        if (v.isBlank()) {
            return;
        }
        upsert.run(
                event,
                base,
                Map.of(
                        "artifactId",
                        "requirements_spec",
                        "sectionId",
                        "narrative",
                        "mode",
                        "replace",
                        "data",
                        Map.of(narrativeField, v)));
    }

    private static int applyUpsertsArray(
            UpsertArtifactSectionDataAction upsertAction,
            Event event,
            Map<String, Object> base,
            JsonNode upsertsNode)
            throws JsonProcessingException {
        if (!upsertsNode.isArray()) {
            return 0;
        }
        int applied = 0;
        for (JsonNode n : upsertsNode) {
            if (!n.isObject()) {
                continue;
            }
            Object aid = n.get("artifactId");
            Object sid = n.get("sectionId");
            if (aid == null || sid == null) {
                continue;
            }
            Object dataObj = n.get("data");
            if (!(dataObj instanceof Map<?, ?>)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) dataObj;
            String mode = n.get("mode") != null ? n.get("mode").asText() : "replace";
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
        return applied;
    }

    private static String composeExplorationMarkdown(FeaturePlanState plan, JsonNode root) {
        String req = plan.getInitialRequest() != null ? plan.getInitialRequest().trim() : "";
        StringBuilder sb = new StringBuilder();
        sb.append("(LLM expansion pass)\n\n");
        if (!req.isBlank()) {
            sb.append("**Original request**\n").append(req).append("\n\n");
        }
        appendBul(sb, "**Current state**", text(root, "current_state"));
        appendBul(sb, "**Proposed behavior**", text(root, "proposed_behavior"));
        appendBul(sb, "**Config / model**", text(root, "config_model"));
        appendBul(sb, "**Selection granularity**", text(root, "selection_granularity"));
        appendBul(sb, "**Change type**", text(root, "change_type"));
        appendBul(sb, "**Compatibility / fallback**", text(root, "compatibility_fallback"));
        appendBul(sb, "**Impacted components**", text(root, "impacted_components"));
        appendBul(sb, "**Validation concerns**", text(root, "validation_concerns"));
        appendBul(sb, "**Design options**", text(root, "design_options"));
        JsonNode oq = root.path("candidate_open_questions");
        if (oq.isArray() && oq.size() > 0) {
            sb.append("**Open questions (candidates)**\n");
            for (JsonNode n : oq) {
                if (n.isTextual()) {
                    sb.append("- ").append(n.asText().trim()).append("\n");
                }
            }
        }
        return sb.toString().trim();
    }

    private static void appendBul(StringBuilder sb, String heading, String body) {
        if (body == null || body.isBlank()) {
            return;
        }
        sb.append(heading).append("\n").append(body).append("\n\n");
    }

    private static String text(JsonNode root, String field) {
        JsonNode n = root.path(field);
        return n.isMissingNode() || !n.isTextual() ? "" : n.asText("").trim();
    }

    private static Map<String, Object> baseSpread() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planningLlmOk", "false");
        m.put("planningLlmError", "");
        m.put("planningExpansionSkipReason", "");
        m.put("planningExpansionFallbackUsed", "false");
        m.put("planningExpansionSource", "");
        m.put("planningExpansionFromLlm", "false");
        m.put("planningExpansionRichBody", "false");
        m.put("planningExpansionFollowUpsJson", "[]");
        m.put("planningExpansionUpsertExtras", "0");
        m.put("planningExpansionLatencyMs", "0");
        return m;
    }

    private static String buildUserPayload(FeaturePlanState plan, String profileId) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("profileId", profileId);
        snap.put("initialRequest", plan.getInitialRequest());
        snap.put("repoRef", plan.getRepoRef());
        snap.put("repoLocalPath", plan.getRepoLocalPath());
        snap.put("existingExploration", PlanningArtifactTexts.artifactField(plan, "request_exploration", "analysis", "exploration_body"));
        try {
            return "Planning expansion context (JSON):\n" + JSON.writerWithDefaultPrettyPrinter().writeValueAsString(snap);
        } catch (JsonProcessingException e) {
            return "Planning expansion context (JSON):\n{}";
        }
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
}
