package com.vinekeepers.workflow;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.StateStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurableWorkflowRunnerTest {

    @Test
    void runEmptyStepsReturnsEmpty() {
        WorkflowDefinition def = new WorkflowDefinition("empty", List.of());
        ConfigurableWorkflowRunner runner = new ConfigurableWorkflowRunner(def, new WorkflowActionRegistry());
        Event event = new Event("test", "message", Map.of());
        StateStore store = new StateStore();
        String out = runner.run(event, store, "bot1");
        assertEquals("", out);
    }

    @Test
    void runSingleDoneStepReturnsMessage() {
        List<Map<String, Object>> steps = List.of(
                Map.of("type", "done", "message", "All done.")
        );
        WorkflowDefinition def = new WorkflowDefinition("single", steps);
        ConfigurableWorkflowRunner runner = new ConfigurableWorkflowRunner(def, new WorkflowActionRegistry());
        Event event = new Event("test", "message", Map.of());
        StateStore store = new StateStore();
        String out = runner.run(event, store, "bot1");
        assertEquals("All done.", out);
    }

    @Test
    void runAskInputThenDoneStoresAndReturns() {
        List<Map<String, Object>> steps = List.of(
                Map.of("type", "ask_input", "prompt", "Say something", "storeIn", "input"),
                Map.of("type", "done", "message", "You said: {{input}}")
        );
        WorkflowDefinition def = new WorkflowDefinition("flow", steps);
        ConfigurableWorkflowRunner runner = new ConfigurableWorkflowRunner(def, new WorkflowActionRegistry());
        Event event = new Event("test", "message", Map.of("content", "hello"));
        StateStore store = new StateStore();
        String out = runner.run(event, store, "bot1");
        assertEquals("You said: hello", out);
        String stateKey = "bot:bot1:state";
        assertTrue(store.contains(stateKey));
        ConfigurableWorkflowState state = store.get(stateKey, ConfigurableWorkflowState.class).orElseThrow();
        assertEquals("hello", state.get("input"));
    }

    @Test
    void runWithDiscordChannelUsesConvStateKey() {
        List<Map<String, Object>> steps = List.of(
                Map.of("type", "done", "message", "OK")
        );
        WorkflowDefinition def = new WorkflowDefinition("x", steps);
        ConfigurableWorkflowRunner runner = new ConfigurableWorkflowRunner(def, new WorkflowActionRegistry());
        Event event = new Event("discord:1", "message", Map.of("channelId", "chan-123"));
        StateStore store = new StateStore();
        runner.run(event, store, "luna");
        assertTrue(store.contains("bot:luna:conv:chan-123"));
    }

    @Test
    void runCallActionStepStoresActionResult() {
        WorkflowActionRegistry registry = new WorkflowActionRegistry();
        registry.register("echo", (e, s, b) -> b.get("message"));
        List<Map<String, Object>> steps = List.of(
                Map.of("type", "call_action", "action", "echo", "bind", Map.of("message", "echoed"), "storeIn", "out"),
                Map.of("type", "done", "message", "Result: {{out}}")
        );
        WorkflowDefinition def = new WorkflowDefinition("action-flow", steps);
        ConfigurableWorkflowRunner runner = new ConfigurableWorkflowRunner(def, registry);
        Event event = new Event("test", "msg", Map.of());
        StateStore store = new StateStore();
        String out = runner.run(event, store, "b");
        assertEquals("Result: echoed", out);
    }
}
