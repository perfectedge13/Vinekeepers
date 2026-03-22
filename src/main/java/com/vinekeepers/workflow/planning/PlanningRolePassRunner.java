package com.vinekeepers.workflow.planning;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs one OpenAI-backed planning pass for Architect (Hypatia), Auditor (Toad), or Scribe (Nyx) roles.
 * Updates canonical artifacts via JSON upserts; no Discord visibility (orchestrator posts summaries separately).
 */
public final class PlanningRolePassRunner {

    private static final Logger log = LoggerFactory.getLogger(PlanningRolePassRunner.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private PlanningRolePassRunner() {}

    public record RolePassResult(int upsertsApplied, List<String> followUps, String error, boolean skipped) {}

    public static RolePassResult run(
            PlanningRole role,
            OpenAiChatClient client,
            FeaturePlanState plan,
            WorkProfileDefinition profile,
            Event event,
            Map<String, Object> state,
            FeaturePlanStateStore planStore,
            WorkProfileRegistry profileRegistry) {
        if (planStore == null || profileRegistry == null || plan == null || profile == null) {
            return new RolePassResult(0, List.of(), "missing deps", true);
        }
        if (client == null || !client.isConfigured()) {
            return new RolePassResult(0, List.of(), "NO_API_KEY", true);
        }
        String contextId = plan.getContextId();
        String system = role.systemPromptBlock();
        String user = buildUserPayload(plan, profile.getProfileId(), role);
        String raw;
        try {
            raw = client.complete(system, user);
        } catch (Exception e) {
            log.warn("{} pass failed: {} — {}", role, e.getClass().getName(), chainBrief(e));
            String msg = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.getClass().getSimpleName();
            return new RolePassResult(0, List.of(), msg, false);
        }
        if (raw.startsWith("ERROR:")) {
            return new RolePassResult(0, List.of(), raw, false);
        }
        try {
            String json = PlanningLlmJsonSupport.extractJsonObject(raw);
            JsonNode root = JSON.readTree(json);
            int applied = PlanningLlmJsonSupport.applyUpserts(root, event, state, contextId, planStore, profileRegistry);
            List<String> followUps = PlanningLlmJsonSupport.readFollowUpQuestions(root);
            return new RolePassResult(applied, followUps, "", false);
        } catch (Exception e) {
            log.warn("{} pass parse failed: {}", role, e.getMessage());
            return new RolePassResult(0, List.of(), e.getMessage() != null ? e.getMessage() : "parse failed", false);
        }
    }

    private static String buildUserPayload(FeaturePlanState plan, String profileId, PlanningRole role) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("profileId", profileId);
        snap.put("role", role.name());
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
            return "Task: " + role.userTaskHint() + "\n\nSnapshot (JSON):\n"
                    + JSON.writerWithDefaultPrettyPrinter().writeValueAsString(snap);
        } catch (Exception e) {
            return "Task: " + role.userTaskHint() + "\n\nSnapshot: {}";
        }
    }

    /** Maps to Architect / Auditor / Scribe room roles (user-facing names Hypatia / Toad / Nyx in docs). */
    public enum PlanningRole {
        ARCHITECT,
        AUDITOR,
        SCRIBE;

        String systemPromptBlock() {
            String schema = """
                    Reply with a single JSON object only, no markdown fences.
                    Schema:
                    {
                      "upserts": [
                        { "artifactId": "requirements_spec", "sectionId": "narrative", "mode": "replace", "data": { "fieldId": "value" } }
                      ],
                      "follow_up_questions": [ "short question?" ]
                    }
                    Use only artifact/section ids that exist in the software_feature_planning_v2 profile.
                    If nothing should change, return {"upserts":[],"follow_up_questions":[]}.
                    """;
            return switch (this) {
                case ARCHITECT -> "You are the Architect for feature intake. Propose structure: impacted components, "
                        + "config vs runtime boundaries, and design tradeoffs. Prefer architecture_notes.impact, "
                        + "overall_plan.outline, and append decision_log.decisions when recording options.\n" + schema;
                case AUDITOR -> "You are the Auditor. Critique risks, mitigations, backward compatibility, edge cases, "
                        + "and whether validation coverage matches the request. Prefer risk_register.main, "
                        + "validation_plan.checks, and open_questions_block.backlog for gaps that block safe implementation.\n"
                        + schema;
                case SCRIBE -> "You are the Scribe. Consolidate narrative clarity: requirements_spec.narrative "
                        + "(feature summary, scope, stories, acceptance), project_context.context, and tighten wording "
                        + "without changing agreed facts.\n" + schema;
            };
        }

        String userTaskHint() {
            return switch (this) {
                case ARCHITECT -> "Update architecture and plan outline from the snapshot; keep proposals concrete.";
                case AUDITOR -> "Stress-test the draft for implementation risk and validation gaps.";
                case SCRIBE -> "Make the packet readable and internally consistent.";
            };
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
