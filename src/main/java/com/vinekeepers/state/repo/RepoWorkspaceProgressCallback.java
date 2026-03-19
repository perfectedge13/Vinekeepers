package com.vinekeepers.state.repo;

/**
 * Optional progress hook for {@link RepoWorkspaceService#ensure(String, String, RepoWorkspaceProgressCallback)}.
 * {@code detail} may be null; meaning depends on {@link RepoWorkspaceProgressPhase}.
 */
@FunctionalInterface
public interface RepoWorkspaceProgressCallback {

    void onProgress(RepoWorkspaceProgressPhase phase, String detail);
}
