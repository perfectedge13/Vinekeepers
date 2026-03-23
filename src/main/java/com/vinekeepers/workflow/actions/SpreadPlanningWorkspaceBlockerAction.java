package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.repo.RepoWorkspaceStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * When the plan's repo workspace is in a terminal failure state, spreads a single concrete user-facing prompt so intake
 * can stop before the first draft (hard precondition only).
 */
public final class SpreadPlanningWorkspaceBlockerAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStateStore;

    public SpreadPlanningWorkspaceBlockerAction(FeaturePlanStateStore planStateStore) {
        this.planStateStore = planStateStore;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("planningWorkspaceUserInputRequired", "false");
        spread.put("planningWorkspaceBlockerPrompt", "");
        if (planStateStore == null) {
            return spread;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            return spread;
        }
        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            return spread;
        }
        String raw = plan.getRepoWorkspaceStatus();
        if (raw == null || raw.isBlank()) {
            return spread;
        }
        try {
            RepoWorkspaceStatus s = RepoWorkspaceStatus.valueOf(raw.trim());
            if (s != RepoWorkspaceStatus.FAILED && s != RepoWorkspaceStatus.UNAVAILABLE) {
                return spread;
            }
        } catch (IllegalArgumentException e) {
            return spread;
        }
        String wsReason =
                "We could not prepare a local workspace for this repository ("
                        + plan.getRepoWorkspaceStatus()
                        + "). "
                        + (plan.getRepoAccessNotes() != null && !plan.getRepoAccessNotes().isBlank()
                                ? plan.getRepoAccessNotes() + " "
                                : "")
                        + "Reply with access notes, a local checkout path, or how you want to proceed.";
        spread.put("planningWorkspaceUserInputRequired", "true");
        spread.put("planningWorkspaceBlockerPrompt", wsReason);
        return spread;
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
