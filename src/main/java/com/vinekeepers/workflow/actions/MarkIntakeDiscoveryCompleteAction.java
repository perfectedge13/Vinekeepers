package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Marks that the user completed structured discovery for manual intake paths.
 * Workflow state key {@code humanDiscoveryCompleted} complements {@link com.vinekeepers.state.planning.PlanningIntakeStage}
 * for critique and approval gates.
 */
public final class MarkIntakeDiscoveryCompleteAction implements com.vinekeepers.workflow.WorkflowAction {

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("humanDiscoveryCompleted", "true");
        return spread;
    }
}
