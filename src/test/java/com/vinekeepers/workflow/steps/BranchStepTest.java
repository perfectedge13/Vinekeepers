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

    @Test
    void branchOperatorEqualsMatchesAndGoesToNext() {
        List<Map<String, Object>> branches = List.of(
                Map.of("when", Map.of("key", "action", "operator", "equals", "value", "launch"), "next", 5),
                Map.of("when", "else", "next", 0)
        );
        BranchStep step = new BranchStep(branches);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        state.put("action", "launch");
        StepResult result = step.execute(new Event("test", "message", Map.of()), state, 0);
        assertEquals(5, result.getNextStepIndex());
    }

    @Test
    void branchOperatorNotEqualsMatches() {
        List<Map<String, Object>> branches = List.of(
                Map.of("when", Map.of("key", "x", "operator", "not_equals", "value", "skip"), "next", 2),
                Map.of("when", "else", "next", 0)
        );
        BranchStep step = new BranchStep(branches);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        state.put("x", "run");
        StepResult result = step.execute(new Event("test", "message", Map.of()), state, 0);
        assertEquals(2, result.getNextStepIndex());
    }

    @Test
    void branchOperatorBlankMatches() {
        List<Map<String, Object>> branches = List.of(
                Map.of("when", Map.of("key", "optional", "operator", "blank"), "next", 1),
                Map.of("when", "else", "next", 0)
        );
        BranchStep step = new BranchStep(branches);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        state.put("optional", "");
        StepResult result = step.execute(new Event("test", "message", Map.of()), state, 0);
        assertEquals(1, result.getNextStepIndex());
    }

    @Test
    void branchOperatorNonblankMatches() {
        List<Map<String, Object>> branches = List.of(
                Map.of("when", Map.of("key", "name", "operator", "nonblank"), "next", 3),
                Map.of("when", "else", "next", 0)
        );
        BranchStep step = new BranchStep(branches);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        state.put("name", "alice");
        StepResult result = step.execute(new Event("test", "message", Map.of()), state, 0);
        assertEquals(3, result.getNextStepIndex());
    }

    @Test
    void branchOperatorContainsMatches() {
        List<Map<String, Object>> branches = List.of(
                Map.of("when", Map.of("key", "text", "operator", "contains", "value", "yes"), "next", 2),
                Map.of("when", "else", "next", 0)
        );
        BranchStep step = new BranchStep(branches);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        state.put("text", "I said yes please");
        StepResult result = step.execute(new Event("test", "message", Map.of()), state, 0);
        assertEquals(2, result.getNextStepIndex());
    }

    @Test
    void branchOperatorStarts_withMatches() {
        List<Map<String, Object>> branches = List.of(
                Map.of("when", Map.of("key", "cmd", "operator", "starts_with", "value", "run:"), "next", 4),
                Map.of("when", "else", "next", 0)
        );
        BranchStep step = new BranchStep(branches);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        state.put("cmd", "run:task1");
        StepResult result = step.execute(new Event("test", "message", Map.of()), state, 0);
        assertEquals(4, result.getNextStepIndex());
    }

    @Test
    void branchOperatorRegexFullStringMatches() {
        List<Map<String, Object>> branches = List.of(
                Map.of("when", Map.of("key", "code", "operator", "regex", "value", "[a-z]+"), "next", 1),
                Map.of("when", "else", "next", 0)
        );
        BranchStep step = new BranchStep(branches);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        state.put("code", "abc");
        StepResult result = step.execute(new Event("test", "message", Map.of()), state, 0);
        assertEquals(1, result.getNextStepIndex());
    }

    @Test
    void branchOperatorOne_ofYamlListMatches() {
        List<Map<String, Object>> branches = List.of(
                Map.of("when", Map.of("key", "choice", "operator", "one_of", "value", List.of("launch", "edit", "cancel")), "next", 2),
                Map.of("when", "else", "next", 0)
        );
        BranchStep step = new BranchStep(branches);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        state.put("choice", "edit");
        StepResult result = step.execute(new Event("test", "message", Map.of()), state, 0);
        assertEquals(2, result.getNextStepIndex());
    }

    @Test
    void branchLegacyFormStringKeyTruthyGoesToNext() {
        List<Map<String, Object>> branches = List.of(
                Map.of("when", "hasProject", "next", 1),
                Map.of("when", "else", "next", 0)
        );
        BranchStep step = new BranchStep(branches);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        state.put("hasProject", "repo/a");
        StepResult result = step.execute(new Event("test", "message", Map.of()), state, 0);
        assertEquals(1, result.getNextStepIndex());
    }

    @Test
    void branchTransformOnStateValueOnly() {
        List<Map<String, Object>> branches = List.of(
                Map.of("when", Map.of("key", "action", "value", "launch", "transform", List.of("trim", "lower")), "next", 3),
                Map.of("when", "else", "next", 0)
        );
        BranchStep step = new BranchStep(branches);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        state.put("action", "  LAUNCH  ");
        StepResult result = step.execute(new Event("test", "message", Map.of()), state, 0);
        assertEquals(3, result.getNextStepIndex());
    }

    @Test
    void branchNoMatchFallsThroughToNextStepIndex() {
        List<Map<String, Object>> branches = List.of(
                Map.of("when", Map.of("key", "x", "value", "yes"), "next", 10),
                Map.of("when", Map.of("key", "x", "value", "maybe"), "next", 5)
        );
        BranchStep step = new BranchStep(branches);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        state.put("x", "no");
        StepResult result = step.execute(new Event("test", "message", Map.of()), state, 2);
        assertEquals(3, result.getNextStepIndex());
    }
}
