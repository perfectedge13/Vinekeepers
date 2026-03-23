package com.vinekeepers.state.planning;

/**
 * Outcome of the post-draft clarification assessor for a single need or gap candidate.
 */
public enum ClarificationResolutionDecision {
    ASK_USER,
    ASSUME_AND_CONTINUE,
    LOW_PRIORITY_DEFER,
    BLOCK_AS_UNIMPLEMENTABLE
}
