package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Marks that the user completed the coordinator discovery kickoff (reply after Luna synthetic handoff).
 * Workflow state key {@code humanDiscoveryCompleted} gates {@link RunPlanCritiqueAndReadinessAction}.
 */
public final class MarkIntakeDiscoveryCompleteAction implements com.vinekeepers.workflow.WorkflowAction {

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("humanDiscoveryCompleted", "true");
        return spread;
    }
}
