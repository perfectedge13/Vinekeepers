package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.WorkflowAction;
import com.vinekeepers.workflow.planning.PlanningCyclePipeline;

import java.util.Map;

/** Workflow-facing planning evaluation step after silent synthesis. */
public final class PlanningRunEvaluationAction implements WorkflowAction {

    private final PlanningCyclePipeline pipeline;

    public PlanningRunEvaluationAction(PlanningCyclePipeline pipeline) {
        this.pipeline = pipeline != null ? pipeline : new PlanningCyclePipeline(null, null, null);
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        return pipeline.runEvaluationOnly(event, state, bind);
    }
}
