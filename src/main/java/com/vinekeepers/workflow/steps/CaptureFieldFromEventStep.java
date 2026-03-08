package com.vinekeepers.workflow.steps;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepResult;
import com.vinekeepers.workflow.WorkflowStep;

/**
 * Conversational step that captures a field from the current event after a previous prompt paused the run.
 */
public final class CaptureFieldFromEventStep implements WorkflowStep {

    private final String storeIn;
    private final String contentKey;

    public CaptureFieldFromEventStep(String storeIn, String contentKey) {
        this.storeIn = storeIn != null ? storeIn : "input";
        this.contentKey = contentKey;
    }

    @Override
    public StepResult execute(Event event, ConfigurableWorkflowState state, int stepIndex) {
        String content = readContent(event);
        return StepResult.advance(storeIn, content);
    }

    private String readContent(Event event) {
        if (event == null) {
            return "";
        }
        if (contentKey != null && !contentKey.isBlank()) {
            String explicit = event.getPayload(contentKey, String.class);
            if (explicit != null) {
                return explicit;
            }
        }
        String content = event.getPayload("content", String.class);
        if (content == null) {
            content = event.getPayload("text", String.class);
        }
        return content != null ? content : "";
    }
}
