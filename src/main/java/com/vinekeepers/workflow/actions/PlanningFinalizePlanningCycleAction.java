package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.planning.PlanningCyclePipeline;

import java.util.Map;

/** Partial planning-room cycle: clarification ranking, orchestrator summary, progress fingerprint. */
public final class PlanningFinalizePlanningCycleAction implements com.vinekeepers.workflow.WorkflowAction {

    private final PlanningCyclePipeline pipeline;

    public PlanningFinalizePlanningCycleAction(PlanningCyclePipeline pipeline) {
        this.pipeline = pipeline != null ? pipeline : new PlanningCyclePipeline(null, null, null);
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        return pipeline.finalizePlanningCycleSpread(event, state, bind);
    }
}
