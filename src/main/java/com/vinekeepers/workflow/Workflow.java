package com.vinekeepers.workflow;

import com.vinekeepers.events.Event;

/**
 * Workflow that processes events and advances state.
 */
public interface Workflow<S> {

    /**
     * Process the event with current state; returns updated state and whether the workflow is done.
     */
    WorkflowResult<S> process(Event event, S state);
}
