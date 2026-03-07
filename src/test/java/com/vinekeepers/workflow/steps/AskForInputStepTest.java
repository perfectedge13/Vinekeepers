package com.vinekeepers.workflow.steps;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AskForInputStepTest {

    @Test
    void executeStoresContentFromPayload() {
        AskForInputStep step = new AskForInputStep("Say something", "input");
        Event event = new Event("discord:1", "message", Map.of("content", "hello world"));
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        StepResult result = step.execute(event, state, 0);
        assertEquals("input", result.getStoreIn());
        assertEquals("hello world", result.getStoreValue());
        assertFalse(result.isDone());
    }

    @Test
    void executeFallsBackToTextKey() {
        AskForInputStep step = new AskForInputStep(null, "reply");
        Event event = new Event("test", "msg", Map.of("text", "fallback"));
        StepResult result = step.execute(event, new ConfigurableWorkflowState(), 0);
        assertEquals("reply", result.getStoreIn());
        assertEquals("fallback", result.getStoreValue());
    }

    @Test
    void executeUsesDefaultStoreInWhenNull() {
        AskForInputStep step = new AskForInputStep("Prompt", null);
        Event event = new Event("test", "msg", Map.of("content", "x"));
        StepResult result = step.execute(event, new ConfigurableWorkflowState(), 0);
        assertEquals("input", result.getStoreIn());
    }

    @Test
    void executeEmptyContentWhenMissing() {
        AskForInputStep step = new AskForInputStep("?", "out");
        Event event = new Event("test", "msg", Map.of());
        StepResult result = step.execute(event, new ConfigurableWorkflowState(), 0);
        assertEquals("", result.getStoreValue());
    }
}
