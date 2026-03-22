package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.planreview.PlanningPacketDepthEvaluator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sets {@code planningPacketDepthOk} and {@code planningPacketDepthReason} for workflow branching before
 * {@link PostPlanningPacketThreadAction}.
 */
public final class EvaluatePlanningPacketDepthAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStateStore;
    private final WorkProfileRegistry workProfileRegistry;

    public EvaluatePlanningPacketDepthAction(FeaturePlanStateStore planStateStore, WorkProfileRegistry workProfileRegistry) {
        this.planStateStore = planStateStore;
        this.workProfileRegistry = workProfileRegistry;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("planningPacketDepthOk", "false");
        spread.put("planningPacketDepthReason", "");
        if (planStateStore == null) {
            spread.put("planningPacketDepthReason", "Plan store not available.");
            return spread;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            spread.put("planningPacketDepthReason", "Missing contextId.");
            return spread;
        }
        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
        WorkProfileDefinition profile = null;
        if (workProfileRegistry != null && plan != null && plan.getProfileId() != null && !plan.getProfileId().isBlank()) {
            profile = workProfileRegistry.get(plan.getProfileId()).orElse(null);
        }
        PlanningPacketDepthEvaluator.DepthResult r = PlanningPacketDepthEvaluator.evaluate(plan, profile);
        spread.put("planningPacketDepthOk", r.ok() ? "true" : "false");
        spread.put("planningPacketDepthReason", r.reason() != null ? r.reason() : "");
        spread.put("planningPacketDepthRetryRecommended", r.ok() ? "false" : "true");
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
