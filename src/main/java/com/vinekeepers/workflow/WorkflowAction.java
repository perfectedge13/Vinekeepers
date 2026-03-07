package com.vinekeepers.workflow;

import com.vinekeepers.events.Event;

import java.util.Map;

/**
 * A named action that can be invoked by CallActionStep with event, state, and bound args.
 */
@FunctionalInterface
public interface WorkflowAction {

    /**
     * Run the action.
     *
     * @param event the current event
     * @param state current workflow state data (read-only view; result may be stored by runner)
     * @param bind  bound arguments from step config (may reference state placeholders)
     * @return result to store (or null)
     */
    Object run(Event event, Map<String, Object> state, Map<String, Object> bind);
}
