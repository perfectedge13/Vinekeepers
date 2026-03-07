package com.vinekeepers.workflow;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.StateStore;

/**
 * Runner for StubWorkflow; state key "bot:" + botId + ":state".
 */
public final class StubWorkflowRunner implements WorkflowRunner {

    private static final StubWorkflow WORKFLOW = new StubWorkflow();

    @Override
    public String run(Event event, StateStore stateStore, String botId) {
        String stateKey = "bot:" + botId + ":state";
        StubState state = stateStore.get(stateKey, StubState.class).orElse(StubState.INITIAL);
        WorkflowResult<StubState> result = WORKFLOW.process(event, state);
        stateStore.put(stateKey, result.getState());
        return result.getMessage();
    }
}
