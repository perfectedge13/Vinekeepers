package com.vinekeepers.state.repo;

/**
 * Milestones during {@link RepoWorkspaceService#ensure(String, String, RepoWorkspaceProgressCallback)} for logging
 * and optional UI (e.g. lifecycle thread updates).
 */
public enum RepoWorkspaceProgressPhase {
    /** Canonical repo ref resolved (informational; often not shown to chat). */
    RESOLVED_REF,
    /** Creating {@code VINEKEEPERS_REPO_WORKSPACE_ROOT} hierarchy. */
    CREATING_WORKSPACE_ROOT,
    /** Deleting a previous clone directory before re-cloning. */
    REMOVING_STALE_CLONE,
    /** Running {@code git clone}. */
    CLONING,
    /** Reading branch/commit after materialization. */
    VERIFYING_GIT,
    /** Local git directory validated. */
    READY_LOCAL,
    /** Shallow clone completed; detail holds branch and short commit. */
    READY_CLONED,
    /** Failure; detail holds a safe user-facing reason. */
    FAILED
}
