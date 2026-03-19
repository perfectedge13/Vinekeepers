package com.vinekeepers.state.planning;

/**
 * Machine readiness for pre-implementation gate (Phase C).
 */
public final class PlanReadinessStatus {

    public static final String BLOCKED = "BLOCKED";
    public static final String NEEDS_REVISION = "NEEDS_REVISION";
    public static final String NEEDS_HUMAN_DECISION = "NEEDS_HUMAN_DECISION";
    public static final String READY = "READY";

    private PlanReadinessStatus() {}
}
