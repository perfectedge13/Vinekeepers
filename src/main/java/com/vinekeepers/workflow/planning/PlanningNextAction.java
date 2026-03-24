package com.vinekeepers.workflow.planning;

/**
 * Minimal live-routing actions after planning evaluation normalization.
 */
public enum PlanningNextAction {
    ASK_USER,
    READY_FOR_PACKET,
    BLOCKED
}
