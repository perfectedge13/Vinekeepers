package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.SolutionOutline;

import java.util.Map;

public final class SetSolutionOutlineAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStateStore;

    public SetSolutionOutlineAction(FeaturePlanStateStore planStateStore) {
        this.planStateStore = planStateStore;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        if (planStateStore == null) {
            return "FeaturePlanStateStore not available.";
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            return "Missing contextId for set_solution_outline.";
        }
        String rawSummary = firstNonBlank(getString(bind, "summary"), getString(bind, "outline"));
        final String outlineSummary = rawSummary != null ? rawSummary : "";
        return planStateStore.getByContextId(contextId)
                .map(p -> {
                    FeaturePlanState next = p.withSolutionOutline(new SolutionOutline(outlineSummary));
                    planStateStore.update(next);
                    return "OK";
                })
                .orElse("No FeaturePlanState for contextId: " + contextId);
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
