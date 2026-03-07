package com.vinekeepers.workflow.steps;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepResult;
import com.vinekeepers.workflow.WorkflowActionRegistry;
import com.vinekeepers.workflow.WorkflowStep;

import java.util.Map;

/**
 * Step that invokes a registered action by id with bound arguments; can store result.
 */
public final class CallActionStep implements WorkflowStep {

    private final WorkflowActionRegistry registry;
    private final String actionId;
    private final Map<String, Object> bind;
    private final String storeIn;

    public CallActionStep(WorkflowActionRegistry registry, String actionId, Map<String, Object> bind, String storeIn) {
        this.registry = registry != null ? registry : new WorkflowActionRegistry();
        this.actionId = actionId != null ? actionId : "";
        this.bind = bind != null ? Map.copyOf(bind) : Map.of();
        this.storeIn = storeIn;
    }

    @Override
    public StepResult execute(Event event, ConfigurableWorkflowState state, int stepIndex) {
        Object result = registry.run(actionId, event,
                state != null ? state.getData() : null, bind);
        return StepResult.advance(storeIn, result);
    }
}
