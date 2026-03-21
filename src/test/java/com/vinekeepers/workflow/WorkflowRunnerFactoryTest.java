package com.vinekeepers.workflow;

import com.vinekeepers.bot.BotDefinition;
import com.vinekeepers.bot.MemoryPolicy;
import com.vinekeepers.bot.ModelProfile;
import com.vinekeepers.bot.Persona;
import com.vinekeepers.bot.ToolPolicy;
import com.vinekeepers.core.cursor.CursorLaunchModel;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.StateStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void createFromBotDefinitionSuppliesBotProfileAsDefaultCursorModelForConfiguredWorkflow() {
        WorkflowActionRegistry registry = new WorkflowActionRegistry();
        registry.register("launch_model", (e, s, b) -> CursorLaunchModel.resolveForLaunch(b));
        BotDefinition bot = new BotDefinition(
                "cursor-bot",
                new Persona("C", ""),
                new ModelProfile("anthropic", "claude-from-profile"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(1024),
                "configured",
                Map.of("steps", List.of(
                        Map.of("type", "call_action", "action", "launch_model", "bind", Map.of(), "storeIn", "out"),
                        Map.of("type", "done", "message", "Result: {{out}}"))));
        WorkflowRunner runner = WorkflowRunnerFactory.create(bot, null, registry, null);
        String out = runner.run(new Event("t", "message", Map.of()), new StateStore(), "cursor-bot");
        assertEquals("Result: claude-from-profile", out);
    }
}
