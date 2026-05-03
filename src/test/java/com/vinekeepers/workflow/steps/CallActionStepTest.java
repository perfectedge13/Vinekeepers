package com.vinekeepers.workflow.steps;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepResult;
import com.vinekeepers.workflow.WorkflowActionRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    void executeAppliesStepModelOverStateAndBind() {
        WorkflowActionRegistry registry = new WorkflowActionRegistry();
        AtomicReference<Map<String, Object>> captured = new AtomicReference<>();
        registry.register("launch_cursor_run", (event, state, bind) -> {
            captured.set(bind);
            return bind.get("model");
        });
        CallActionStep step = new CallActionStep(
                registry,
                null,
                null,
                "launch_cursor_run",
                Map.of("model", "from-bind"),
                "modelOut",
                "from-step");
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        state.put("model", "from-state");
        StepResult result = step.execute(new Event("t", "k", Map.of()), state, 0);
        assertEquals("from-step", result.getStoreValue());
        assertEquals("from-step", captured.get().get("model"));
    }
}
