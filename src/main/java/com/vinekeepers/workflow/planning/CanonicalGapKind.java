package com.vinekeepers.workflow.planning;

/**
 * Semantic family for a coordinator planning gap. Derived from stable profile rule id (not from question wording).
 */
public enum CanonicalGapKind {
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
            return CONFIG_VS_RUNTIME_SCOPE;
        }
        if (ID_CONFIG_RUNTIME.equalsIgnoreCase(id)) {
            return CONFIG_RUNTIME_SPECIFICS;
        }
        if (ID_MODEL_OVERRIDE.equalsIgnoreCase(id)) {
            return MODEL_OVERRIDE_GRANULARITY;
        }
        return COORDINATOR_OTHER;
    }
}
