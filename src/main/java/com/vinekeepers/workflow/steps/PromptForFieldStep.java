package com.vinekeepers.workflow.steps;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepResult;
import com.vinekeepers.workflow.WorkflowStep;

/**
 * Conversational step that prompts once and pauses the workflow until a later event supplies the field.
 */
public final class PromptForFieldStep implements WorkflowStep {

    private final String prompt;
    private final String storeIn;

    public PromptForFieldStep(String prompt, String storeIn) {
        this.prompt = prompt != null ? prompt : "";
        this.storeIn = storeIn != null ? storeIn : "input";
    }

    @Override
    public StepResult execute(Event event, ConfigurableWorkflowState state, int stepIndex) {
        Object existing = state != null ? state.get(storeIn) : null;
        if (existing instanceof String value && !value.isBlank()) {
            return StepResult.advance(null, null);
        }
        if (existing != null) {
            return StepResult.advance(null, null);
        }
        return StepResult.waiting(prompt, storeIn);
    }
}
