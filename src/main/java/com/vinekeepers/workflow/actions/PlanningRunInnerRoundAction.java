package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.planning.PlanningCyclePipeline;

import java.util.Map;

/** Partial planning-room cycle: one drafting inner round (roles + expand + synth + depth). */
public final class PlanningRunInnerRoundAction implements com.vinekeepers.workflow.WorkflowAction {

    private final PlanningCyclePipeline pipeline;

    public PlanningRunInnerRoundAction(PlanningCyclePipeline pipeline) {
        this.pipeline = pipeline != null ? pipeline : new PlanningCyclePipeline(null, null, null);
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        return pipeline.runInnerRoundOnce(event, state, bind);
    }
}
