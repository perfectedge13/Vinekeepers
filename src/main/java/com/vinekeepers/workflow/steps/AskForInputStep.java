package com.vinekeepers.workflow.steps;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepResult;
import com.vinekeepers.workflow.WorkflowStep;

/**
 * Step that reads user input from the event and stores it under a key.
 */
public final class AskForInputStep implements WorkflowStep {

    private final String prompt;
    private final String storeIn;

    public AskForInputStep(String prompt, String storeIn) {
        this.prompt = prompt != null ? prompt : "";
        this.storeIn = storeIn != null ? storeIn : "input";
    }

    @Override
    public StepResult execute(com.vinekeepers.events.Event event, ConfigurableWorkflowState state, int stepIndex) {
        String content = event.getPayload("content", String.class);
        if (content == null) content = event.getPayload("text", String.class);
        if (content == null) content = "";
        return StepResult.advance(storeIn, content);
    }
}
