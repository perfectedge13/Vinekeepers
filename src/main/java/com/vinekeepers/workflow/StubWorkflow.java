package com.vinekeepers.workflow;

import com.vinekeepers.events.Event;

/**
 * Stub workflow for testing/wiring (no-op state).
 */
public final class StubWorkflow implements Workflow<StubState> {

    @Override
    public WorkflowResult<StubState> process(Event event, StubState state) {
        return WorkflowResult.done(state, "stub");
    }
}
