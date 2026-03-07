package com.vinekeepers.workflow.steps;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoneStepTest {

    @Test
    void executeReturnsDoneWithMessage() {
        DoneStep step = new DoneStep("Goodbye.");
        StepResult result = step.execute(new Event("t", "k", Map.of()), new ConfigurableWorkflowState(), 0);
        assertTrue(result.isDone());
        assertEquals("Goodbye.", result.getMessage());
    }

    @Test
    void executeNullMessageUsesDefault() {
        DoneStep step = new DoneStep(null);
        StepResult result = step.execute(new Event("t", "k", Map.of()), new ConfigurableWorkflowState(), 0);
        assertEquals("Done.", result.getMessage());
    }

    @Test
    void executeSubstitutesPlaceholdersFromState() {
        DoneStep step = new DoneStep("Hello {{name}}, you said {{input}}.");
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        state.put("name", "Luna");
        state.put("input", "hi");
        StepResult result = step.execute(new Event("t", "k", Map.of()), state, 0);
        assertEquals("Hello Luna, you said hi.", result.getMessage());
    }

    @Test
    void executePlaceholderWithEmptyValueReplacesWithEmpty() {
        DoneStep step = new DoneStep("Value: {{x}}");
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        state.put("x", "");
        StepResult result = step.execute(new Event("t", "k", Map.of()), state, 0);
        assertEquals("Value: ", result.getMessage());
    }
}
