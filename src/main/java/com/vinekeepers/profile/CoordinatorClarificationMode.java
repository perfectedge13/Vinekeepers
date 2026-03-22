package com.vinekeepers.profile;

/**
 * How coordinator clarification rounds are chosen: legacy LLM-follow-up ranking vs canonical gap evaluation.
 */
public enum CoordinatorClarificationMode {
    /** Prior behavior: rank aggregated LLM follow-ups; ledger OPEN drives {@code planningUserInputRequired}. */
    LEGACY,
    /** Gaps derived from plan + profile rules; LLM strings only trigger configured gaps. */
    CANONICAL_V1
}
