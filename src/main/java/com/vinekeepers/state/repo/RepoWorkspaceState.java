package com.vinekeepers.state.repo;

import java.time.Instant;
import java.util.Objects;

/**
 * Canonical record of repo workspace resolution / materialization for a lifecycle context.
 */
public final class RepoWorkspaceState {

    private final String contextId;
    private final String repoRef;
    private final String repoDisplayName;
    private final String workspaceId;
    private final String localPath;
    private final String branch;
    private final String commit;
    private final RepoMaterializationMode materializationMode;
    private final RepoWorkspaceStatus status;
    private final String failureReason;
    private final Instant createdAt;
    private final Instant updatedAt;

    public RepoWorkspaceState(
            String contextId,
            String repoRef,
            String repoDisplayName,
            String workspaceId,
            String localPath,
            String branch,
            String commit,
            RepoMaterializationMode materializationMode,
            RepoWorkspaceStatus status,
            String failureReason,
            Instant createdAt,
            Instant updatedAt) {
        this.contextId = Objects.requireNonNull(contextId, "contextId");
        this.repoRef = repoRef;
        this.repoDisplayName = repoDisplayName;
        this.workspaceId = workspaceId;
        this.localPath = localPath;
        this.branch = branch;
        this.commit = commit;
        this.materializationMode = materializationMode != null ? materializationMode : RepoMaterializationMode.UNKNOWN;
        this.status = status != null ? status : RepoWorkspaceStatus.UNRESOLVED;
        this.failureReason = failureReason;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    public String getContextId() {
        return contextId;
    }

    public String getRepoRef() {
        return repoRef;
    }

    public String getRepoDisplayName() {
        return repoDisplayName;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public String getLocalPath() {
        return localPath;
    }

    public String getBranch() {
        return branch;
    }

    public String getCommit() {
        return commit;
    }

    public RepoMaterializationMode getMaterializationMode() {
        return materializationMode;
    }

    public RepoWorkspaceStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
