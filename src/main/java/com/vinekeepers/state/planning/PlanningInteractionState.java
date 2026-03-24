package com.vinekeepers.state.planning;

/**
 * Canonical interaction expectation for the current planning state.
 */
public enum PlanningInteractionState {
    NONE,
    WAITING_FOR_TEXT_REPLY,
    WAITING_FOR_APPROVAL_INTERACTION
}
