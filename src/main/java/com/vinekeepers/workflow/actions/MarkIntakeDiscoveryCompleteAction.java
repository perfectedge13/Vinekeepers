package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.PlanningIntakeStage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Marks that the user completed structured discovery for manual intake paths and advances canonical intake state.
 */
public final class MarkIntakeDiscoveryCompleteAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStateStore;

    public MarkIntakeDiscoveryCompleteAction(FeaturePlanStateStore planStateStore) {
        this.planStateStore = planStateStore;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (planStateStore != null && contextId != null && !contextId.isBlank()) {
            planStateStore.getByContextId(contextId).ifPresent(plan -> {
                if (plan.getPlanningIntakeStage() == PlanningIntakeStage.GATHERING_CONTEXT) {
                    planStateStore.update(plan.withPlanningIntakeStage(PlanningIntakeStage.DRAFTING, null));
                }
            });
        }
        spread.put("canonicalPlanningIntakeStage", PlanningIntakeStage.DRAFTING.name());
        return spread;
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }
}
