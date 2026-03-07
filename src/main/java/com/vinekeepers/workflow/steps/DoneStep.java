package com.vinekeepers.workflow.steps;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepResult;
import com.vinekeepers.workflow.WorkflowStep;

/**
 * Step that ends the workflow with a message.
 */
public final class DoneStep implements WorkflowStep {

    private final String message;

    public DoneStep(String message) {
        this.message = message != null ? message : "Done.";
    }

    @Override
    public StepResult execute(Event event, ConfigurableWorkflowState state, int stepIndex) {
        String resolved = message;
        if (state != null && state.getData() != null) {
            for (String key : state.getData().keySet()) {
                Object v = state.get(key);
                String placeholder = "{{" + key + "}}";
                if (resolved.contains(placeholder)) {
                    resolved = resolved.replace(placeholder, v != null ? v.toString() : "");
                }
            }
        }
        return StepResult.done(resolved);
    }
}
