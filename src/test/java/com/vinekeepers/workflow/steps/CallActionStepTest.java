package com.vinekeepers.workflow.steps;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepResult;
import com.vinekeepers.workflow.WorkflowActionRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallActionStepTest {

    @Test
    void executeCallsActionAndStoresResult() {
        WorkflowActionRegistry registry = new WorkflowActionRegistry();
        registry.register("echo", (event, state, bind) -> bind.get("message"));
        CallActionStep step = new CallActionStep(registry, "echo", Map.of("message", "hi"), "out");
        Event event = new Event("test", "msg", Map.of());
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        StepResult result = step.execute(event, state, 0);
        assertEquals("out", result.getStoreIn());
        assertEquals("hi", result.getStoreValue());
        assertFalse(result.isDone());
    }

    @Test
    void executeMissingActionStoresNull() {
        WorkflowActionRegistry registry = new WorkflowActionRegistry();
        CallActionStep step = new CallActionStep(registry, "missing", Map.of(), "result");
        StepResult result = step.execute(new Event("t", "k", Map.of()), new ConfigurableWorkflowState(), 0);
        assertEquals("result", result.getStoreIn());
        assertEquals(null, result.getStoreValue());
    }

    @Test
    void executeWithNullRegistryUsesEmptyRegistry() {
        CallActionStep step = new CallActionStep(null, "any", Map.of(), "x");
        StepResult result = step.execute(new Event("t", "k", Map.of()), new ConfigurableWorkflowState(), 0);
        assertEquals(null, result.getStoreValue());
    }

    @Test
    void executeStoreSpreadMergesMapIntoStepResult() {
        WorkflowActionRegistry registry = new WorkflowActionRegistry();
        registry.register("multi", (e, s, b) -> Map.of("k1", "a", "k2", 2));
        CallActionStep step = new CallActionStep(registry, null, null, "multi", Map.of(), null, true);
        StepResult result = step.execute(new Event("t", "k", Map.of()), new ConfigurableWorkflowState(), 0);
        assertTrue(result.getSpreadWrites().containsKey("k1"));
        assertEquals("a", result.getSpreadWrites().get("k1"));
        assertEquals(2, result.getSpreadWrites().get("k2"));
    }
}
