package com.vinekeepers.workflow.planning;

/**
 * Configurable coordinator passes for feature-room planning (single consolidated coordinator pass).
 */
public enum PlanningCoordinatorRole {
    COORDINATOR;

    String systemPromptBlock() {
        String schema = """
                Reply with a single JSON object only, no markdown fences, no commentary before or after.
                Syntax rules: use [ ] for arrays and { } for objects only; do not close an array with }; no trailing commas.
                Contract rules: sectionId must be a real profile section id like narrative, impact, backlog, outline,
                checks, context, analysis, or decisions. data keys must be real field ids for that section. Never use
                the literal key "fieldId" and never use a field id such as scope_summary or open_questions as sectionId.
                Schema:
                {
                  "upserts": [
                    { "artifactId": "requirements_spec", "sectionId": "narrative", "mode": "replace", "data": { "feature_summary": "value", "current_state_summary": "value", "scope_summary": "value" } },
                    { "artifactId": "open_questions_block", "sectionId": "backlog", "mode": "replace", "data": { "open_questions": "None - ready to implement" } }
                  ],
                  "follow_up_questions": [ "short question?" ]
                }
                Use only artifact/section ids that exist in the software_feature_planning_v2 profile.
                If nothing should change, return {"upserts":[],"follow_up_questions":[]}.
                """;
        return switch (this) {
            case COORDINATOR -> "You are the sole planning coordinator for this feature room. "
                    + "You must preserve the coverage previously split across architecture, audit, and scribe passes. "
                    + "Produce a coherent planning draft that covers requirements_spec.narrative, architecture_notes.impact, "
                    + "risk_register.main, open_questions_block.backlog, decision_log.decisions, overall_plan.outline, "
                    + "validation_plan.checks, project_context.context, and request_exploration.analysis when the snapshot "
                    + "supports improving them. Keep the draft concrete, internally consistent, and implementation-ready.\n"
                    + schema;
        };
    }

    String userTaskHint() {
        return switch (this) {
            case COORDINATOR -> "Update the full planning packet as one coordinator pass without dropping architecture, risk, validation, or narrative coverage.";
        };
    }
}
