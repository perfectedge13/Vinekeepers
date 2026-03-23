package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.planning.PlanningCyclePipeline;

import java.util.Map;
import java.util.Objects;

/**
 * Workflow action facade for one planning-room cycle; delegates to {@link PlanningCyclePipeline}.
 */
public final class ExecutePlanningRoomCycleAction implements com.vinekeepers.workflow.WorkflowAction {

    private final PlanningCyclePipeline pipeline;

    public ExecutePlanningRoomCycleAction(PlanningCyclePipeline pipeline) {
        this.pipeline = Objects.requireNonNull(pipeline, "PlanningCyclePipeline");
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        return pipeline.execute(event, state, bind);
    }
}
