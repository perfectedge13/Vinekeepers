package com.vinekeepers.workflow.planning;

/**
 * Semantic family for a coordinator planning gap. Derived from stable profile rule id (not from question wording).
 */
public enum CanonicalGapKind {
    BLOCKING_CONSTRAINT,
    BRANCHING_DECISION,
    CONTRADICTION,
    UNVERIFIED_ASSUMPTION,
    WEAK_VALIDATION,
    MISSING_AUTHORITY,
    MISSING_IMPLEMENTATION_SCOPE,
    MISSING_ROLLOUT_BOUNDARY,
    CONFIG_VS_RUNTIME_SCOPE,
    CONFIG_RUNTIME_SPECIFICS,
    MODEL_OVERRIDE_GRANULARITY,
    COORDINATOR_OTHER;

    private static final String ID_CONFIG_VS_RUNTIME = "config_vs_runtime_scope";
    private static final String ID_CONFIG_RUNTIME = "config_runtime_specifics";
    private static final String ID_MODEL_OVERRIDE = "model_override_granularity";

    public static CanonicalGapKind fromRuleId(String ruleId) {
        if (ruleId == null) {
            return COORDINATOR_OTHER;
        }
        String id = ruleId.trim();
        if (ID_CONFIG_VS_RUNTIME.equalsIgnoreCase(id)) {
            return BRANCHING_DECISION;
        }
        if (ID_CONFIG_RUNTIME.equalsIgnoreCase(id)) {
            return MISSING_IMPLEMENTATION_SCOPE;
        }
        if (ID_MODEL_OVERRIDE.equalsIgnoreCase(id)) {
            return BRANCHING_DECISION;
        }
        if ("blocking_constraint".equalsIgnoreCase(id)) {
            return BLOCKING_CONSTRAINT;
        }
        if ("branching_decision".equalsIgnoreCase(id)) {
            return BRANCHING_DECISION;
        }
        if ("contradiction".equalsIgnoreCase(id)) {
            return CONTRADICTION;
        }
        if ("unverified_assumption".equalsIgnoreCase(id)) {
            return UNVERIFIED_ASSUMPTION;
        }
        if ("weak_validation".equalsIgnoreCase(id)) {
            return WEAK_VALIDATION;
        }
        if ("missing_authority".equalsIgnoreCase(id)) {
            return MISSING_AUTHORITY;
        }
        if ("missing_implementation_scope".equalsIgnoreCase(id)) {
            return MISSING_IMPLEMENTATION_SCOPE;
        }
        if ("missing_rollout_boundary".equalsIgnoreCase(id)) {
            return MISSING_ROLLOUT_BOUNDARY;
        }
        return COORDINATOR_OTHER;
    }
}
