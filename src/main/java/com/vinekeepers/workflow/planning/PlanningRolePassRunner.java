package com.vinekeepers.workflow.planning;

import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;

import java.util.List;
import java.util.Map;

/**
 * Runs one OpenAI-backed planning pass for Architect (Hypatia), Auditor (Toad), or Scribe (Nyx) roles.
 * Delegates execution to {@link StructuredLlmArtifactUpsertPass}.
 */
public final class PlanningRolePassRunner {

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
        return StructuredLlmArtifactUpsertPass.execute(
                client,
                plan,
                profile,
                event,
                state,
                planStore,
                profileRegistry,
                role.name(),
                role.systemPromptBlock(),
                role.userTaskHint());
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
}
