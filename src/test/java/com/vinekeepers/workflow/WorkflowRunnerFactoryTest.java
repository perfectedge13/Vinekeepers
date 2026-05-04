package com.vinekeepers.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowRunnerFactoryTest {

    @Test
    void createStubReturnsStubWorkflowRunner() {
        WorkflowRunner runner = WorkflowRunnerFactory.create("stub", null);
        assertNotNull(runner);
        assertTrue(runner instanceof StubWorkflowRunner);
    }

    @Test
    void createNullOrBlankTypeReturnsStubWorkflowRunner() {
        assertTrue(WorkflowRunnerFactory.create(null, null) instanceof StubWorkflowRunner);
        assertTrue(WorkflowRunnerFactory.create("", null) instanceof StubWorkflowRunner);
        assertTrue(WorkflowRunnerFactory.create("   ", null) instanceof StubWorkflowRunner);
    }

    @Test
    void createUnknownTypeReturnsStubWorkflowRunner() {
        WorkflowRunner runner = WorkflowRunnerFactory.create("unknown", Map.of());
        assertNotNull(runner);
        assertTrue(runner instanceof StubWorkflowRunner);
    }

    @Test
    void createConfiguredWithWorkflowRefReturnsConfigurableWorkflowRunner() {
        Map<String, Object> workflows = Map.of(
                "greet", Map.of("steps", List.of(
                        Map.of("type", "done", "message", "Hello")
                ))
        );
        Map<String, Object> params = Map.of("workflowRef", "greet");
        WorkflowRunner runner = WorkflowRunnerFactory.create("configured", params, workflows, new WorkflowActionRegistry());
        assertNotNull(runner);
        assertTrue(runner instanceof ConfigurableWorkflowRunner);
    }

    @Test
    void createConfiguredWithInlineStepsReturnsConfigurableWorkflowRunner() {
        Map<String, Object> params = Map.of("steps", List.of(
                Map.of("type", "done", "message", "Done.")
        ));
        WorkflowRunner runner = WorkflowRunnerFactory.create("configured", params, null, null);
        assertNotNull(runner);
        assertTrue(runner instanceof ConfigurableWorkflowRunner);
    }

    @Test
    void createConfiguredWithNullWorkflowsAndParamsReturnsEmptyDefinitionRunner() {
        WorkflowRunner runner = WorkflowRunnerFactory.create("configured", null, null, new WorkflowActionRegistry());
        assertNotNull(runner);
        assertTrue(runner instanceof ConfigurableWorkflowRunner);
    }

    @Test
    void createConfiguredWithLlmStepAndNoModelSourceThrows() {
        Map<String, Object> workflows = Map.of(
                "luna_flow", Map.of("steps", List.of(
                        Map.of("type", "call_action", "action", "launch_cursor_run", "bind", Map.of())
                ))
        );
        Map<String, Object> params = Map.of("workflowRef", "luna_flow");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                WorkflowRunnerFactory.create("configured", params, workflows, new WorkflowActionRegistry()));
        assertTrue(error.getMessage().contains("no model configured for LLM step"));
    }

    @Test
    void createConfiguredRejectsUnsupportedStepModel() {
        Map<String, Object> workflows = Map.of(
                "luna_flow", Map.of("steps", List.of(
                        Map.of("type", "call_action", "action", "launch_cursor_run", "model", "not-a-real-model", "bind", Map.of())
                ))
        );
        Map<String, Object> params = Map.of("workflowRef", "luna_flow");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                WorkflowRunnerFactory.create("configured", params, workflows, new WorkflowActionRegistry()));
        assertTrue(error.getMessage().contains("Unsupported workflow step model"));
    }

    @Test
    void createConfiguredRejectsModelOnNonLlmStep() {
        Map<String, Object> workflows = Map.of(
                "luna_flow", Map.of("steps", List.of(
                        Map.of("type", "done", "message", "All done", "model", "gpt-4o-mini")
                ))
        );
        Map<String, Object> params = Map.of("workflowRef", "luna_flow");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                WorkflowRunnerFactory.create("configured", params, workflows, new WorkflowActionRegistry()));
        assertTrue(error.getMessage().contains("model override is only allowed on LLM call_action steps"));
    }

    @Test
    void createConfiguredAcceptsWorkflowDefaultModelWithoutStepOverride() {
        Map<String, Object> workflows = Map.of(
                "luna_flow", Map.of(
                        "defaultModel", "gpt-4o-mini",
                        "steps", List.of(Map.of("type", "call_action", "action", "launch_cursor_run", "bind", Map.of()))
                )
        );
        Map<String, Object> params = Map.of("workflowRef", "luna_flow");

        WorkflowRunner runner = WorkflowRunnerFactory.create("configured", params, workflows, new WorkflowActionRegistry());
        assertNotNull(runner);
        assertTrue(runner instanceof ConfigurableWorkflowRunner);
    }
}
