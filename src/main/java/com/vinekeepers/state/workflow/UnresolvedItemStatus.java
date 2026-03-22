package com.vinekeepers.state.workflow;

/**
 * Lifecycle status for a generic unresolved item in workflow domain state.
 */
public enum UnresolvedItemStatus {
    OPEN,
    ANSWERED,
    MERGED,
    RESOLVED_ASSUMPTION,
    BLOCKED,
    CANCELLED,
    /** Closed without merge; same fingerprint may surface again after artifact invalidation. */
    INVALIDATED
}
