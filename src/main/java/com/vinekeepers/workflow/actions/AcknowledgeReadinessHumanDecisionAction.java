package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.planning.PlanningReadinessSpread;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records that the user acknowledged the readiness checkpoint and wants approval-gate evaluation to continue.
 */
public final class AcknowledgeReadinessHumanDecisionAction implements com.vinekeepers.workflow.WorkflowAction {

    public AcknowledgeReadinessHumanDecisionAction(
            com.vinekeepers.state.planning.FeaturePlanStateStore planStateStore,
            com.vinekeepers.profile.WorkProfileRegistry workProfileRegistry) {}

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put(PlanningReadinessSpread.HUMAN_READINESS_ACKNOWLEDGED_KEY, "true");
        return spread;
    }
}
