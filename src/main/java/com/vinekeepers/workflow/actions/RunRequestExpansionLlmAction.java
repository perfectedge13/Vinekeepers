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
import com.vinekeepers.workflow.planning.OpenAiPlanningContentGenerator;
import com.vinekeepers.workflow.planning.PlanningContentGenerator;
import com.vinekeepers.workflow.planning.PlanningLlmJsonSupport;
import com.vinekeepers.workflow.planning.PlanningMaterialSpreadKeys;
import com.vinekeepers.workflow.planreview.PlanningArtifactTexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request-expansion LLM phase: structured JSON merged into artifacts before deterministic exploration build.
 */
public final class RunRequestExpansionLlmAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final Logger log = LoggerFactory.getLogger(RunRequestExpansionLlmAction.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SYSTEM = """
            You are a planning assistant. Reply with a single JSON object only, no markdown fences.
            Contract rules: use real artifactId/sectionId pairs from the active profile, use real field ids inside data,
            never use the literal key "fieldId", and never put a field id in sectionId.
            Schema:
            {
              "repo_evidence_this_pass": "observed | inferred_unverified | not_inspected",
              "current_state": "string",
              "proposed_behavior": "string",
              "config_model": "string",
              "selection_granularity": "string",
              "change_type": "string",
              "compatibility_fallback": "string",
              "impacted_components": "string — only concrete paths/packages if repo_evidence_this_pass is observed; otherwise say unknown or name assumptions explicitly",
              "top_unresolved_gap": "string or empty if none",
              "implementation_scope_notes": "string — draft notes on scope/implementation boundaries (does not control routing)",
              "explicit_assumptions": ["short assumption strings when inferring"],
              "draft_question_candidate": "optional string — at most one possible clarification phrased as draft text only; empty if none; does not control routing",
              "validation_concerns": "string",
              "design_options": "optional string",
              "upserts": [ { "artifactId": "requirements_spec", "sectionId": "narrative", "mode": "replace", "data": { "current_state_summary": "value", "feature_summary": "value", "scope_summary": "value" } } ]
            }
            Ground every factual claim: separate what you observed in the repo snapshot this pass vs what you inferred vs unknown.
            Do not fabricate file paths or packages when repo_evidence_this_pass is not observed.
            Do not emit routing or next-step action fields; the evaluation pass alone decides clarification vs packet readiness.
            Populate fields from the user's feature request and repo snapshot. Avoid echoing the request verbatim as the only content.
            Return exactly one JSON object as the entire response body. Do not add commentary before or after it.
            If you are unsure, prefer an empty or conservative JSON object over malformed JSON, prose, or placeholder keys.
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

        String userPayload = buildUserPayload(plan, profileId, state);
        String model = firstNonBlank(getString(bind, "llmModel"), getString(state, "workflowLlmModel"));
        Long timeout = parseTimeoutMs(firstNonBlank(getString(bind, "llmTimeoutMs"), getString(state, "workflowLlmTimeoutMs")));

        long t0 = System.currentTimeMillis();
        String raw;
        try {
            OpenAiCallContext callCtx =
                    OpenAiCallContext.planning(
                            event,
                            state,
                            "Expanding your request into structured planning notes.");
            raw = generator.complete(openAiChatClient, SYSTEM, userPayload, model, timeout, callCtx);
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

        PlanningLlmJsonSupport.ParsedJsonObjectResult parsed =
                PlanningLlmJsonSupport.parseJsonObjectWithRepair(openAiChatClient, raw, "request expansion", event, state);
        spread.put("planningExpansionRepairAttempted", parsed.repairAttempted() ? "true" : "false");
        spread.put("planningExpansionRepairExhausted", parsed.repairExhausted() ? "true" : "false");
        if (!parsed.success()) {
            spread.put("planningLlmError", parsed.errorMessage());
            spread.put("planningExpansionFallbackUsed", "true");
            spread.put("planningExpansionSource", "DETERMINISTIC");
            spread.put("planningLlmOk", "false");
            log.warn("Request expansion parse/apply failed: {}", spread.get("planningLlmError"));
            return spread;
        }

        try {
            JsonNode root = parsed.root();
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

            PlanningLlmJsonSupport.UpsertApplyResult extraResult =
                    PlanningLlmJsonSupport.applyUpsertsArrayDetailed(
                            root.path("upserts"), event, state, contextId, planStateStore, workProfileRegistry);
            int appliedExtras = extraResult.applied();
            spread.put("planningExpansionUpsertsAttempted", String.valueOf(extraResult.attempted()));
            spread.put("planningExpansionUpsertsRejected", String.valueOf(extraResult.rejected().size()));
            if (extraResult.attempted() > 0 && appliedExtras == 0) {
                spread.put("planningLlmError", PlanningLlmJsonSupport.summarizeRejectedUpserts(extraResult));
                spread.put("planningExpansionFallbackUsed", "true");
                spread.put("planningExpansionSource", "DETERMINISTIC");
                spread.put("planningLlmOk", "false");
                return spread;
            }

            spread.put("planningExpansionUpsertExtras", String.valueOf(appliedExtras));
            spread.put("planningExpansionFromLlm", "true");
            spread.put("planningExpansionRichBody", explorationBody.length() >= 200 ? "true" : "false");
            spread.put("planningExpansionSource", "LLM");
            spread.put("planningExpansionFallbackUsed", "false");
            spread.put("planningLlmOk", "true");
            spread.put("planningLlmError", "");
            spread.put(
                    PlanningMaterialSpreadKeys.EXPANSION_DRAFT_QUESTION_CANDIDATE_KEY,
                    PlanningLlmJsonSupport.readDraftQuestionCandidate(root));
            spread.put(
                    PlanningMaterialSpreadKeys.EXPANSION_TOP_UNRESOLVED_GAP_KEY,
                    PlanningLlmJsonSupport.readTopUnresolvedGap(root));
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
        appendBul(sb, "**Implementation scope notes**", text(root, "implementation_scope_notes"));
        appendBul(sb, "**Draft question candidate**", text(root, "draft_question_candidate"));
        JsonNode asm = root.path("explicit_assumptions");
        if (asm.isArray() && asm.size() > 0) {
            sb.append("**Explicit assumptions**\n");
            for (JsonNode n : asm) {
                if (n.isTextual()) {
                    String t = n.asText().trim();
                    if (!t.isBlank()) {
                        sb.append("- ").append(t).append("\n");
                    }
                }
            }
            sb.append("\n");
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
        m.put("planningExpansionUpsertExtras", "0");
        m.put("planningExpansionUpsertsAttempted", "0");
        m.put("planningExpansionUpsertsRejected", "0");
        m.put("planningExpansionRepairAttempted", "false");
        m.put("planningExpansionRepairExhausted", "false");
        m.put("planningExpansionLatencyMs", "0");
        m.put(PlanningMaterialSpreadKeys.EXPANSION_DRAFT_QUESTION_CANDIDATE_KEY, "");
        m.put(PlanningMaterialSpreadKeys.EXPANSION_TOP_UNRESOLVED_GAP_KEY, "");
        return m;
    }

    private static String buildUserPayload(FeaturePlanState plan, String profileId, Map<String, Object> state) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("profileId", profileId);
        snap.put("initialRequest", plan.getInitialRequest());
        snap.put("repoRef", plan.getRepoRef());
        snap.put("repoLocalPath", plan.getRepoLocalPath());
        snap.put("existingExploration", PlanningArtifactTexts.artifactField(plan, "request_exploration", "analysis", "exploration_body"));
        String evidenceJson = "";
        if (state != null) {
            String ev = getString(state, "planningRepoEvidenceJson");
            if (ev != null && !ev.isBlank()) {
                evidenceJson = ev;
                snap.put("repo_evidence_snapshot", ev);
            }
            if ("true".equalsIgnoreCase(String.valueOf(state.get("planningRagAvailable")))) {
                String rag =
                        state.get("planningRagRetrievalText") != null ? state.get("planningRagRetrievalText").toString() : "";
                if (rag != null && !rag.isBlank()) {
                    snap.put("repo_grounding_snippets", rag);
                }
            }
        }
        boolean tangibleWorkspace =
                (plan.getRepoLocalPath() != null && !plan.getRepoLocalPath().isBlank())
                        || (evidenceJson != null
                                && !evidenceJson.isBlank()
                                && !evidenceJson.trim().equals("{}"));
        if (tangibleWorkspace) {
            snap.put(
                    "planner_instruction",
                    "Workspace or repo snapshot is present in this context: prefer repo_evidence_this_pass=observed when you "
                            + "actually use paths/snippets from the snapshot; put concrete paths/packages in impacted_components "
                            + "when the evidence supports them. Avoid generic repo-agnostic planning while real workspace signals exist.");
        }
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
