package com.vinekeepers.workflow.steps;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BranchStepTest {

    @Test
    void branchWithClearReturnsStepResultWithClearKeys() {
        List<Map<String, Object>> branches = List.of(
                Map.of("when", "else", "next", 0, "clear", List.of("project", "repo"))
        );
        BranchStep step = new BranchStep(branches);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        Event event = new Event("test", "message", Map.of());

        StepResult result = step.execute(event, state, 0);

        assertEquals(0, result.getNextStepIndex());
        assertEquals(List.of("project", "repo"), result.getClearKeys());
        assertFalse(result.getClearKeys().isEmpty());
    }

    @Test
    void branchWithoutClearReturnsStepResultWithEmptyClearKeys() {
        List<Map<String, Object>> branches = List.of(
                Map.of("when", "else", "next", 2)
        );
        BranchStep step = new BranchStep(branches);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        Event event = new Event("test", "message", Map.of());

        StepResult result = step.execute(event, state, 0);

        assertEquals(2, result.getNextStepIndex());
        assertEquals(List.of(), result.getClearKeys());
    }

    @Test
    void branchWithEmptyStringValueMatchesEmptyState() {
        List<Map<String, Object>> branches = List.of(
                Map.of("when", Map.of("key", "roomName", "value", ""), "next", 1),
                Map.of("when", "else", "next", 0)
        );
        BranchStep step = new BranchStep(branches);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        state.put("roomName", "");
        Event event = new Event("test", "message", Map.of());

        StepResult result = step.execute(event, state, 0);

        assertEquals(1, result.getNextStepIndex());
    }
}
