package com.vinekeepers.state.repo;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for {@link RepoWorkspaceState}. Indexed by contextId, workspaceId, repoRef.
 */
public final class RepoWorkspaceStateStore {

    private final ConcurrentHashMap<String, RepoWorkspaceState> byContextId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RepoWorkspaceState> byWorkspaceId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RepoWorkspaceState> byRepoRef = new ConcurrentHashMap<>();

    public void put(RepoWorkspaceState state) {
        if (state == null) {
            return;
        }
        RepoWorkspaceState existing = byContextId.get(state.getContextId());
        if (existing != null) {
            if (existing.getWorkspaceId() != null && !existing.getWorkspaceId().isBlank()) {
                byWorkspaceId.remove(existing.getWorkspaceId());
            }
            if (existing.getRepoRef() != null && !existing.getRepoRef().isBlank()) {
                byRepoRef.remove(existing.getRepoRef());
            }
        }
        byContextId.put(state.getContextId(), state);
        if (state.getWorkspaceId() != null && !state.getWorkspaceId().isBlank()) {
            byWorkspaceId.put(state.getWorkspaceId(), state);
        }
        if (state.getRepoRef() != null && !state.getRepoRef().isBlank()) {
            byRepoRef.put(state.getRepoRef(), state);
        }
    }

    public Optional<RepoWorkspaceState> getByContextId(String contextId) {
        if (contextId == null || contextId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byContextId.get(contextId));
    }

    public Optional<RepoWorkspaceState> getByWorkspaceId(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byWorkspaceId.get(workspaceId));
    }

    public Optional<RepoWorkspaceState> getByRepoRef(String repoRef) {
        if (repoRef == null || repoRef.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byRepoRef.get(repoRef));
    }

    public void update(RepoWorkspaceState state) {
        put(state);
    }
}
