package com.vinekeepers.state.repo;

/**
 * Lifecycle status for repo workspace materialization.
 */
public enum RepoWorkspaceStatus {
    UNRESOLVED,
    RESOLVED_LOCAL,
    MATERIALIZED,
    UNAVAILABLE,
    FAILED
}
