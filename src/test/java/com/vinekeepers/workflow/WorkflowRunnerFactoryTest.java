package com.vinekeepers.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void createConfiguredRejectsUnsupportedStepModel() {
        Map<String, Object> params = Map.of("steps", List.of(
                Map.of("type", "call_action", "action", "launch_cursor_run", "model", "unsupported-model")
        ));
        assertThrows(IllegalArgumentException.class,
                () -> WorkflowRunnerFactory.create(
                        "configured",
                        params,
                        null,
                        new WorkflowActionRegistry(),
                        null,
                        com.vinekeepers.bot.ToolPolicy.allowAll(),
                        com.vinekeepers.bot.ConversationMode.SINGLE_EVENT,
                        null,
                        null,
                        "gpt-4o-mini",
                        Set.of("gpt-4o-mini")));
    }
}
