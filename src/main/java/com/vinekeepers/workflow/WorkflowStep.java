package com.vinekeepers.workflow;

import com.vinekeepers.events.Event;

/**
 * A single step in a configurable workflow; executes in the context of event and state.
 */
public interface WorkflowStep {

    /**
     * Execute this step with the current event and state.
     *
     * @param event        the incoming event
     * @param state        mutable configurable workflow state
     * @param stepIndex    current step index (0-based)
     * @return result indicating next step, optional store, and whether workflow is done
     */
    StepResult execute(Event event, ConfigurableWorkflowState state, int stepIndex);
}
