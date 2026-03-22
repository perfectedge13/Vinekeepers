package com.vinekeepers.state.planning;

/**
 * Machine readiness for pre-implementation gate (Phase C).
 */
public final class PlanReadinessStatus {

    public static final String BLOCKED = "BLOCKED";
    public static final String NEEDS_REVISION = "NEEDS_REVISION";
    public static final String NEEDS_HUMAN_DECISION = "NEEDS_HUMAN_DECISION";
    public static final String READY = "READY";
    /** Explicit not-ready umbrella (maps from BLOCKED / NEEDS_REVISION in new policy). */
    public static final String NOT_READY = "NOT_READY";
    /** Proceed only with documented assumptions / waivers. */
    public static final String CONDITIONALLY_READY = "CONDITIONALLY_READY";

    private PlanReadinessStatus() {}

    /** Spread / YAML compatibility: expose primary readiness for branches that still key on legacy strings. */
    public static String legacySpreadValue(String readiness) {
        if (readiness == null || readiness.isBlank()) {
            return "";
        }
        if (NOT_READY.equals(readiness)) {
            return NEEDS_REVISION;
        }
        if (CONDITIONALLY_READY.equals(readiness)) {
            return NEEDS_HUMAN_DECISION;
        }
        return readiness;
    }
}
