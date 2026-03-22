package com.vinekeepers.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
    void createConfiguredWithWorkflowSchemaV2ReturnsGraphRunner() {
        java.util.LinkedHashMap<String, Object> wf = new java.util.LinkedHashMap<>();
        wf.put("workflowSchema", "v2");
        wf.put("entryPhase", "done");
        wf.put("phases", Map.of("done", Map.of("pipeline", List.of(), "terminal", true)));
        wf.put("capabilities", Map.of());
        Map<String, Object> workflows = Map.of("gv2", wf);
        Map<String, Object> params = Map.of("workflowRef", "gv2");
        WorkflowRunner runner =
                WorkflowRunnerFactory.create("configured", params, workflows, new WorkflowActionRegistry());
        assertNotNull(runner);
        assertTrue(runner instanceof com.vinekeepers.workflow.v2.GraphWorkflowRunner);
    }
}
