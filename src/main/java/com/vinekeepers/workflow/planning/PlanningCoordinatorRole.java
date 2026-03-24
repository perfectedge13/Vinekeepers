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
                If you are unsure, return {"upserts":[],"question_if_needed":"","recommended_action":"CONTINUE_SYNTHESIS"} instead of prose, placeholders, or malformed JSON.
                Schema:
                {
                  "repo_evidence_this_pass": "observed | inferred_unverified | not_inspected",
                  "upserts": [
                    { "artifactId": "requirements_spec", "sectionId": "narrative", "mode": "replace", "data": { "feature_summary": "value", "current_state_summary": "value", "scope_summary": "value" } }
                  ],
                  "top_unresolved_gap": "string or empty",
                  "recommended_action": "ASK_USER | CONTINUE_SYNTHESIS | READY_FOR_PACKET | BLOCK",
                  "question_if_needed": "single string or empty",
                  "explicit_assumptions": ["short assumption"]
                }
                Use only artifact/section ids that exist in the software_feature_planning_v2 profile.
                If nothing should change, return {"upserts":[],"explicit_assumptions":[],"question_if_needed":"","top_unresolved_gap":"","recommended_action":"CONTINUE_SYNTHESIS","repo_evidence_this_pass":"not_inspected"}.
                """;
        return switch (this) {
            case COORDINATOR -> "You are the sole planning coordinator for this feature room. "
                    + "Ground every change in repo evidence, explicit assumptions, and the top unresolved gap. "
                    + "Improve the planning draft only where the snapshot supports a more concrete, internally consistent, "
                    + "implementation-ready result across requirements, architecture, risks, decisions, validation, context, "
                    + "and exploration.\n"
                    + schema;
        };
    }

    String userTaskHint() {
        return switch (this) {
            case COORDINATOR -> "Tighten the draft using repo-grounded facts, explicit assumptions, and the top unresolved gap; ask at most one question only when the canonical next action is ASK_USER.";
        };
    }
}
