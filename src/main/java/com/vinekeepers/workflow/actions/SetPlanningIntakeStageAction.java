package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.PlanningIntakeStage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists an explicit planning intake stage transition for phase-owned v2 workflows.
 */
public final class SetPlanningIntakeStageAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStateStore;

    public SetPlanningIntakeStageAction(FeaturePlanStateStore planStateStore) {
        this.planStateStore = planStateStore;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        String rawStage = firstNonBlank(getString(bind, "stage"), getString(state, "planningIntakeStageTarget"));
        if (planStateStore == null || contextId == null || contextId.isBlank() || rawStage == null || rawStage.isBlank()) {
            spread.put("canonicalPlanningIntakeStage", firstNonBlank(getString(state, "canonicalPlanningIntakeStage"), ""));
            return spread;
        }
        PlanningIntakeStage stage;
        try {
            stage = PlanningIntakeStage.valueOf(rawStage.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            spread.put("canonicalPlanningIntakeStage", firstNonBlank(getString(state, "canonicalPlanningIntakeStage"), ""));
            return spread;
        }
        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            spread.put("canonicalPlanningIntakeStage", firstNonBlank(getString(state, "canonicalPlanningIntakeStage"), ""));
            return spread;
        }
        if (plan.getPlanningIntakeStage() != stage) {
            planStateStore.update(plan.withPlanningIntakeStage(stage, null));
        }
        spread.put("canonicalPlanningIntakeStage", stage.name());
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
