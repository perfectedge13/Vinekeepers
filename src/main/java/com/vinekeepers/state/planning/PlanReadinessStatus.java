package com.vinekeepers.state.planning;

/**
 * Machine readiness for pre-implementation gate (Phase C).
 */
public final class PlanReadinessStatus {

    public static final String BLOCKED = "BLOCKED";
    public static final String NEEDS_REVISION = "NEEDS_REVISION";
    public static final String NEEDS_HUMAN_DECISION = "NEEDS_HUMAN_DECISION";
    /** Packet is coherent enough for human review, but not yet ready for approval. */
    public static final String REVIEWABLE = "REVIEWABLE";
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
        if (NEEDS_REVISION.equals(readiness)) {
            return NOT_READY;
        }
        if (NEEDS_HUMAN_DECISION.equals(readiness)) {
            return REVIEWABLE;
        }
        return readiness;
    }
}
