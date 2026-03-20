package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanStateStore;

import java.util.HashMap;
import java.util.Map;

/**
 * Re-runs Phase C critique spread after the user chose to proceed from {@code NEEDS_HUMAN_DECISION},
 * forcing persisted readiness to {@code READY} for the approval step while retaining findings.
 */
public final class AcknowledgeReadinessHumanDecisionAction implements com.vinekeepers.workflow.WorkflowAction {

    private final RunPlanCritiqueAndReadinessAction critique;

    public AcknowledgeReadinessHumanDecisionAction(
            FeaturePlanStateStore planStateStore,
            WorkProfileRegistry workProfileRegistry) {
        this.critique = new RunPlanCritiqueAndReadinessAction(planStateStore, workProfileRegistry);
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> merged = new HashMap<>();
        if (bind != null) {
            merged.putAll(bind);
        }
        merged.put("humanReadinessProceedAck", "true");
        return critique.run(event, state, merged);
    }
}
