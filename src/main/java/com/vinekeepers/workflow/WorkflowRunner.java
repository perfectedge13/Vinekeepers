package com.vinekeepers.workflow;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.StateStore;

/**
 * Runs a workflow for an event: loads state, runs workflow, persists state, returns reply message.
 */
public interface WorkflowRunner {

    /**
     * Run the workflow for the given event and bot; persist state in stateStore.
     *
     * @param event      the incoming event
     * @param stateStore store for workflow state
     * @param botId      bot id (for state keying)
     * @return reply message to send (e.g. to Discord), or null/empty if none
     */
    String run(Event event, StateStore stateStore, String botId);
}
