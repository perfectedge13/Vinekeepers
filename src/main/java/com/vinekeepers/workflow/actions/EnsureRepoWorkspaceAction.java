package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.repo.RepoWorkspaceService;
import com.vinekeepers.state.repo.RepoWorkspaceState;
import com.vinekeepers.state.repo.RepoWorkspaceStateStore;
import com.vinekeepers.state.repo.RepoWorkspaceStatus;

import java.util.Map;

/**
 * Resolves/materializes a repo workspace, stores {@link RepoWorkspaceState}, and links {@link FeaturePlanState}.
 */
public final class EnsureRepoWorkspaceAction implements com.vinekeepers.workflow.WorkflowAction {

    private final RepoWorkspaceService repoWorkspaceService;
    private final RepoWorkspaceStateStore repoWorkspaceStateStore;
    private final FeaturePlanStateStore planStateStore;
    private final FeatureRoomStateStore featureRoomStateStore;

    public EnsureRepoWorkspaceAction(
            RepoWorkspaceService repoWorkspaceService,
            RepoWorkspaceStateStore repoWorkspaceStateStore,
            FeaturePlanStateStore planStateStore,
            FeatureRoomStateStore featureRoomStateStore) {
        this.repoWorkspaceService = repoWorkspaceService;
        this.repoWorkspaceStateStore = repoWorkspaceStateStore;
        this.planStateStore = planStateStore;
        this.featureRoomStateStore = featureRoomStateStore;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        if (repoWorkspaceService == null || repoWorkspaceStateStore == null) {
            return "Repo workspace services not available.";
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            return "Missing contextId for ensure_repo_workspace.";
        }

        String rawRepo = firstNonBlank(getString(bind, "repo"), getString(state, "project"));
        if (rawRepo == null || rawRepo.isBlank()) {
            rawRepo = planStateStore != null
                    ? planStateStore.getByContextId(contextId).map(FeaturePlanState::getRepoRef).orElse(null)
                    : null;
        }
        if (rawRepo == null || rawRepo.isBlank()) {
            rawRepo = featureRoomStateStore != null
                    ? featureRoomStateStore.getByContextId(contextId).map(r -> r.getRepo()).orElse(null)
                    : null;
        }
        if (rawRepo == null || rawRepo.isBlank()) {
            return "Missing repo input for ensure_repo_workspace.";
        }

        RepoWorkspaceState ensured = repoWorkspaceService.ensure(contextId, rawRepo);
        repoWorkspaceStateStore.put(ensured);

        if (planStateStore != null) {
            planStateStore.getByContextId(contextId).ifPresent(plan -> {
                String notes = ensured.getFailureReason();
                if (notes == null || notes.isBlank()) {
                    if (ensured.getStatus() == RepoWorkspaceStatus.UNAVAILABLE) {
                        notes = "Workspace unavailable";
                    }
                }
                FeaturePlanState linked = plan.withWorkspaceLinkage(
                        ensured.getWorkspaceId(),
                        ensured.getStatus() != null ? ensured.getStatus().name() : null,
                        ensured.getLocalPath(),
                        notes);
                planStateStore.update(linked);
            });
        }

        return "OK";
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }
}
