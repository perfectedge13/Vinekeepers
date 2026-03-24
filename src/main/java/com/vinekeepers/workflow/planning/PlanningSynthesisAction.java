package com.vinekeepers.workflow.planning;

/**
 * Deterministic routing signal after evaluation and synthesis pacing.
 */
public enum PlanningSynthesisAction {
    /** Surface exactly one coordinator clarification question and wait for the user. */
    ASK_USER,
    /** Proceed without another silent synthesis pass. */
    ASSUME_AND_CONTINUE,
    /** Draft and clarification gates are satisfied; post the planning packet. */
    READY_FOR_PACKET,
    /** Terminal failure (hard clarification block, exhausted retries, or unrecoverable cycle error). */
    BLOCK,
    /** Run another silent synthesis pass; only when material inputs changed or the revision situation is new. */
    CONTINUE_SYNTHESIS
}
