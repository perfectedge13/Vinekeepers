package com.vinekeepers.state.planning;

/**
 * Canonical routing actions after planning-cycle normalization.
 */
public enum PlanningCanonicalNextAction {
    ASK_USER,
    CONTINUE_SYNTHESIS,
    READY_FOR_PACKET,
    BLOCK
}
