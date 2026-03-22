package com.vinekeepers.workflow.planning;

/**
 * Configurable coordinator passes (Architect / Auditor / Scribe); order is driven by
 * {@link ConfigurablePassRunner} from workflow bind/state.
 */
public enum PlanningCoordinatorRole {
    ARCHITECT,
    AUDITOR,
    SCRIBE;

    String systemPromptBlock() {
        String schema = """
                Reply with a single JSON object only, no markdown fences, no commentary before or after.
                Syntax rules: use [ ] for arrays and { } for objects only; do not close an array with }; no trailing commas.
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
