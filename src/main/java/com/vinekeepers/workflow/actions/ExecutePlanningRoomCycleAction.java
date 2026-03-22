package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.planning.PlanningCyclePipeline;

import java.util.Map;

/**
 * Workflow action facade for one planning-room cycle; delegates to {@link PlanningCyclePipeline}.
 */
public final class ExecutePlanningRoomCycleAction implements com.vinekeepers.workflow.WorkflowAction {

    private final PlanningCyclePipeline pipeline;

    public ExecutePlanningRoomCycleAction(
            OpenAiChatClient openAiChatClient,
            FeaturePlanStateStore planStateStore,
            WorkProfileRegistry workProfileRegistry) {
        this.pipeline = new PlanningCyclePipeline(openAiChatClient, planStateStore, workProfileRegistry);
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        return pipeline.execute(event, state, bind);
    }
}
