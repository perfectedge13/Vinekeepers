package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.planning.PlanningCyclePipeline;

import java.util.Map;

/** Partial planning-room cycle: expansion LLM + request exploration only. */
public final class PlanningRunExpansionPhaseAction implements com.vinekeepers.workflow.WorkflowAction {

    private final PlanningCyclePipeline pipeline;

    public PlanningRunExpansionPhaseAction(PlanningCyclePipeline pipeline) {
        this.pipeline = pipeline != null ? pipeline : new PlanningCyclePipeline(null, null, null);
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        return pipeline.runExpansionPhaseOnly(event, state, bind);
    }
}
