package com.vinekeepers.state.planning;

/**
 * Canonical routing actions after planning-cycle normalization.
 */
public enum PlanningCanonicalNextAction {
    ASK_ONE_QUESTION,
    AUTONOMOUS_REDRAFT,
    POST_PACKET,
    BLOCK
}
