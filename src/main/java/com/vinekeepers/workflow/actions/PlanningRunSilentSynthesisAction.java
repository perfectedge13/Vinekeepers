package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.WorkflowAction;
import com.vinekeepers.workflow.planning.PlanningCyclePipeline;

import java.util.Map;

/** Workflow-facing silent synthesis step: preflight plus drafting internals, without evaluation/routing. */
public final class PlanningRunSilentSynthesisAction implements WorkflowAction {

    private final PlanningCyclePipeline pipeline;

    public PlanningRunSilentSynthesisAction(PlanningCyclePipeline pipeline) {
        this.pipeline = pipeline != null ? pipeline : new PlanningCyclePipeline(null, null, null);
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        return pipeline.runSilentSynthesisOnly(event, state, bind);
    }
}
