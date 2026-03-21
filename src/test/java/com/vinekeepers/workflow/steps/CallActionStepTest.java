package com.vinekeepers.workflow.steps;

import com.vinekeepers.core.cursor.CursorLaunchModel;
import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepResult;
import com.vinekeepers.workflow.WorkflowActionRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CallActionStepTest {

    private static WorkflowActionRegistry registryResolvingLaunchModel() {
        WorkflowActionRegistry registry = new WorkflowActionRegistry();
        registry.register("launch_model", (event, state, bind) -> CursorLaunchModel.resolveForLaunch(bind));
        return registry;
    }

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
    void executeAppliesStepModelWhenBindOmitsModelKeys() {
        WorkflowActionRegistry registry = registryResolvingLaunchModel();
        CallActionStep step = new CallActionStep(registry, null, null, "launch_model", Map.of(), "out", "step-id");
        StepResult result = step.execute(new Event("t", "k", Map.of()), new ConfigurableWorkflowState(), 0);
        assertEquals("step-id", result.getStoreValue());
    }

    @Test
    void executeBindModelKeyOverridesStepModel() {
        WorkflowActionRegistry registry = registryResolvingLaunchModel();
        CallActionStep step = new CallActionStep(registry, null, null, "launch_model",
                Map.of("model", "bind-model"), "out", "step-id");
        StepResult result = step.execute(new Event("t", "k", Map.of()), new ConfigurableWorkflowState(), 0);
        assertEquals("bind-model", result.getStoreValue());
    }

    @Test
    void executeBindCursorModelOverridesStepModel() {
        WorkflowActionRegistry registry = registryResolvingLaunchModel();
        CallActionStep step = new CallActionStep(registry, null, null, "launch_model",
                Map.of("cursorModel", "bind-cursor"), "out", "step-id");
        StepResult result = step.execute(new Event("t", "k", Map.of()), new ConfigurableWorkflowState(), 0);
        assertEquals("bind-cursor", result.getStoreValue());
    }
}
