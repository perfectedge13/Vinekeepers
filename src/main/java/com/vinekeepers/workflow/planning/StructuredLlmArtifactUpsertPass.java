package com.vinekeepers.workflow.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.connectors.openai.OpenAiCallContext;
import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.planning.PlanningRolePassRunner.RolePassResult;
import com.vinekeepers.workflow.planreview.PlanningArtifactTexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic LLM structured JSON upsert pass over feature-plan artifacts (used by {@link PlanningRolePassRunner}).
 */
public final class StructuredLlmArtifactUpsertPass {

    private static final Logger log = LoggerFactory.getLogger(StructuredLlmArtifactUpsertPass.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    /** Prefix on {@link RolePassResult#error()} when assistant output could not be parsed as structured JSON. */
    public static final String STRUCTURED_JSON_PARSE_PREFIX = "STRUCTURED_JSON_PARSE:";
    private static final int REPAIR_USER_SNIPPET_MAX = 14_000;

    private static final String JSON_REPAIR_SYSTEM =
            "You fix JSON. Reply with a single valid JSON object only: no markdown fences, no explanation. "
                    + "Arrays must use [ ]. Objects must use { }. No trailing commas.";

    private StructuredLlmArtifactUpsertPass() {}

    public static RolePassResult execute(
            OpenAiChatClient client,
            FeaturePlanState plan,
            WorkProfileDefinition profile,
            Event event,
            Map<String, Object> state,
            FeaturePlanStateStore planStore,
            WorkProfileRegistry profileRegistry,
            String roleNameForPayload,
            String systemPromptBlock,
            String userTaskHint) {
        if (planStore == null || profileRegistry == null || plan == null || profile == null) {
            return new RolePassResult(0, List.of(), "missing deps", true);
        }
        if (client == null || !client.isConfigured()) {
            return new RolePassResult(0, List.of(), "NO_API_KEY", true);
        }
        String contextId = plan.getContextId();
        String system = systemPromptBlock != null ? systemPromptBlock : "";
        String user = buildUserPayload(plan, profile.getProfileId(), roleNameForPayload, userTaskHint);
        String raw;
        try {
            OpenAiCallContext callCtx =
                    OpenAiCallContext.planning(event, state, coordinatorPassActivityLine(roleNameForPayload));
            raw = client.complete(system, user, null, null, callCtx);
        } catch (Exception e) {
            log.warn("{} pass failed: {} — {}", roleNameForPayload, e.getClass().getName(), chainBrief(e));
            String msg = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.getClass().getSimpleName();
            return new RolePassResult(0, List.of(), msg, false);
        }
        if (raw.startsWith("ERROR:")) {
            return new RolePassResult(0, List.of(), raw, false);
        }
        try {
            return parseAndApplyUpserts(raw, event, state, contextId, planStore, profileRegistry, roleNameForPayload);
        } catch (Exception e) {
            log.warn("{} pass parse failed: {}", roleNameForPayload, e.getMessage());
            String repaired = tryRepairJson(client, raw, roleNameForPayload, event, state);
            if (repaired != null) {
                try {
                    return parseAndApplyUpserts(
                            repaired, event, state, contextId, planStore, profileRegistry, roleNameForPayload);
                } catch (Exception e2) {
                    log.warn("{} pass parse failed after repair: {}", roleNameForPayload, e2.getMessage());
                }
            }
            String brief = e.getMessage() != null ? truncateOneLine(e.getMessage(), 400) : "parse failed";
            return new RolePassResult(0, List.of(), STRUCTURED_JSON_PARSE_PREFIX + brief, false);
        }
    }

    private static RolePassResult parseAndApplyUpserts(
            String raw,
            Event event,
            Map<String, Object> state,
            String contextId,
            FeaturePlanStateStore planStore,
            WorkProfileRegistry profileRegistry,
            String roleNameForPayload)
            throws Exception {
        String json = PlanningLlmJsonSupport.extractJsonObject(raw);
        JsonNode root = JSON.readTree(json);
        int applied = PlanningLlmJsonSupport.applyUpserts(root, event, state, contextId, planStore, profileRegistry);
        List<String> followUps = PlanningLlmJsonSupport.readFollowUpQuestions(root);
        return new RolePassResult(applied, followUps, "", false);
    }

    private static String tryRepairJson(
            OpenAiChatClient client,
            String rawAssistant,
            String roleNameForPayload,
            Event event,
            Map<String, Object> state) {
        if (client == null || !client.isConfigured()) {
            return null;
        }
        String snippet = rawAssistant != null && rawAssistant.length() > REPAIR_USER_SNIPPET_MAX
                ? rawAssistant.substring(0, REPAIR_USER_SNIPPET_MAX) + "…"
                : rawAssistant;
        String user =
                "The following text was meant to be one JSON object but is invalid. "
                        + "Return only corrected JSON (same keys/shape intent: upserts array, follow_up_questions array).\n\n"
                        + snippet;
        String out;
        try {
            OpenAiCallContext repairCtx =
                    OpenAiCallContext.planning(
                            event,
                            state,
                            jsonRepairActivityLine(roleNameForPayload));
            out = client.complete(JSON_REPAIR_SYSTEM, user, "gpt-4o-mini", null, repairCtx);
        } catch (Exception e) {
            log.debug("{} JSON repair call failed: {}", roleNameForPayload, e.getMessage());
            return null;
        }
        if (out == null || out.startsWith("ERROR:")) {
            return null;
        }
        return out;
    }

    private static String coordinatorPassActivityLine(String roleNameForPayload) {
        String r = roleNameForPayload != null ? roleNameForPayload.trim().toUpperCase() : "";
        String who =
                switch (r) {
                    case "COORDINATOR", "ARRIETTY", "ARCHITECT", "AUDITOR", "SCRIBE" -> "Coordinator";
                    default -> "Coordinator";
                };
        return who + " is updating the structured plan draft (asking ChatGPT).";
    }

    private static String jsonRepairActivityLine(String roleNameForPayload) {
        String r = roleNameForPayload != null ? roleNameForPayload.trim().toUpperCase() : "";
        String who =
                switch (r) {
                    case "COORDINATOR", "ARRIETTY", "ARCHITECT", "AUDITOR", "SCRIBE" -> "Coordinator";
                    default -> "Coordinator";
                };
        return "Fixing " + who + " JSON output so we can apply updates (asking ChatGPT).";
    }

    private static String truncateOneLine(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\r\n", " ").replace("\n", " ").trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    private static String buildUserPayload(
            FeaturePlanState plan, String profileId, String roleName, String userTaskHint) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("profileId", profileId);
        snap.put("role", roleName != null ? roleName : "");
        snap.put("initialRequest", plan.getInitialRequest());
        snap.put("repoRef", plan.getRepoRef());
        snap.put("repoLocalPath", plan.getRepoLocalPath());
        snap.put("exploration", PlanningArtifactTexts.artifactField(plan, "request_exploration", "analysis", "exploration_body"));
        snap.put("feature_summary", PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "feature_summary"));
        snap.put("current_state_summary", PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "current_state_summary"));
        snap.put("scope_summary", PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "scope_summary"));
        snap.put("user_stories", PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "user_stories"));
        snap.put("acceptance_criteria", PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "acceptance_criteria"));
        snap.put("open_questions", PlanningArtifactTexts.artifactField(plan, "open_questions_block", "backlog", "open_questions"));
        snap.put("plan_body", PlanningArtifactTexts.artifactField(plan, "overall_plan", "outline", "plan_body"));
        snap.put("architecture_summary", PlanningArtifactTexts.artifactField(plan, "architecture_notes", "impact", "architecture_summary"));
        snap.put("components_impacted", PlanningArtifactTexts.artifactField(plan, "architecture_notes", "impact", "components_impacted"));
        snap.put("risk_summary", PlanningArtifactTexts.artifactField(plan, "risk_register", "main", "risk_summary"));
        snap.put("validation_notes", PlanningArtifactTexts.artifactField(plan, "validation_plan", "checks", "validation_notes"));
        try {
            String hint = userTaskHint != null ? userTaskHint : "";
            return "Task: " + hint + "\n\nSnapshot (JSON):\n"
                    + JSON.writerWithDefaultPrettyPrinter().writeValueAsString(snap);
        } catch (Exception e) {
            return "Task: " + (userTaskHint != null ? userTaskHint : "") + "\n\nSnapshot: {}";
        }
    }

    private static String chainBrief(Throwable t) {
        if (t == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (Throwable c = t; c != null && depth < 5; c = c.getCause(), depth++) {
            if (depth > 0) {
                sb.append(" | ");
            }
            sb.append(c.getClass().getSimpleName());
            if (c.getMessage() != null && !c.getMessage().isBlank()) {
                sb.append(": ").append(c.getMessage());
            }
        }
        return sb.toString();
    }
}
