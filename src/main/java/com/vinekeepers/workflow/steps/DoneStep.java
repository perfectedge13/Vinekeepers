package com.vinekeepers.workflow.steps;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepResult;
import com.vinekeepers.workflow.WorkflowStep;
import com.vinekeepers.workflow.template.WorkflowTemplateInterpolator;
import com.vinekeepers.workflow.template.WorkflowTemplatePolicy;

/**
 * Step that ends the workflow with a message.
 */
public final class DoneStep implements WorkflowStep {

    private final String message;
    private final WorkflowTemplatePolicy templatePolicy;

    public DoneStep(String message) {
        this(message, WorkflowTemplatePolicy.LEGACY_FULL_STATE);
    }

    public DoneStep(String message, WorkflowTemplatePolicy templatePolicy) {
        this.message = message != null ? message : "Done.";
        this.templatePolicy = templatePolicy != null ? templatePolicy : WorkflowTemplatePolicy.LEGACY_FULL_STATE;
    }

    @Override
    public StepResult execute(Event event, ConfigurableWorkflowState state, int stepIndex) {
        String resolved = WorkflowTemplateInterpolator.interpolate(message, state, templatePolicy);
        return StepResult.done(resolved);
    }
}
