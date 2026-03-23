package com.vinekeepers.workflow.planning;

/**
 * Single post-assess routing signal for Arrietty v2 {@code planning_assess_clarification} rulesets.
 * {@link PlanningPostDraftGovernor} derives this from canonical clarification, packet depth, parse health, and
 * material-change / non-improving-pass guards (no legacy boolean-first routing).
 */
public enum PlanningPostDraftAction {
    /** Surface exactly one coordinator clarification question and wait for the user. */
    ASK_ONE_QUESTION,
    /** Proceed without another autonomous drafting loop (assumptions recorded or non-improving stall). */
    ASSUME_AND_CONTINUE,
    /** Draft and clarification gates are satisfied; post the planning packet. */
    POST_PACKET,
    /** Terminal failure (hard clarification block, exhausted retries, or unrecoverable cycle error). */
    BLOCK,
    /** Run another autonomous planning pass; only when material inputs changed or the revision situation is new. */
    AUTONOMOUS_REDRAFT
}
