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
     * @return structured workflow run result
     */
    WorkflowRunResult runResult(Event event, StateStore stateStore, String botId);

    /**
     * Legacy adapter for older callers that still expect a reply string.
     */
    default String run(Event event, StateStore stateStore, String botId) {
        return runResult(event, stateStore, botId).getReplyMessage();
    }
}
