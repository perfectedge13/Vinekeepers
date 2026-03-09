package com.vinekeepers.workflow;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.StateStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void runPromptAndCapturePausesThenResumesAtCaptureStep() {
        List<Map<String, Object>> steps = List.of(
                Map.of("type", "prompt_for_field", "prompt", "Which project?", "storeIn", "project"),
                Map.of("type", "capture_field", "storeIn", "project"),
                Map.of("type", "done", "message", "Project: {{project}}")
        );
        WorkflowDefinition def = new WorkflowDefinition("conversation", steps);
        ConfigurableWorkflowRunner runner = new ConfigurableWorkflowRunner(def, new WorkflowActionRegistry());
        StateStore store = new StateStore();

        Event firstEvent = new Event("discord:test", "message",
                Map.of("channelId", "chan-1", "authorId", "user-1", "content", "/Luna"));
        WorkflowRunResult firstResult = runner.runResult(firstEvent, store, "luna");

        assertTrue(firstResult.isWaiting());
        assertEquals("Which project?", firstResult.getReplyMessage());
        assertEquals("project", firstResult.getWaitingForField());

        String stateKey = "bot:luna:conv:chan-1:user-1";
        ConfigurableWorkflowState waitingState = store.get(stateKey, ConfigurableWorkflowState.class).orElseThrow();
        assertEquals(ConfigurableWorkflowState.Status.WAITING_INPUT, waitingState.getStatus());
        assertEquals(1, waitingState.getStepIndex());
        assertEquals("project", waitingState.getWaitingForField());
        assertEquals("Which project?", waitingState.getPendingPrompt());

        Event resumeEvent = new Event("discord:test", "message",
                Map.of("channelId", "chan-1", "authorId", "user-1", "content", "vinekeepers"));
        WorkflowRunResult resumed = runner.runResult(resumeEvent, store, "luna");

        assertFalse(resumed.isWaiting());
        assertTrue(resumed.isCompleted());
        assertEquals("Project: vinekeepers", resumed.getReplyMessage());

        ConfigurableWorkflowState completedState = store.get(stateKey, ConfigurableWorkflowState.class).orElseThrow();
        assertEquals(ConfigurableWorkflowState.Status.COMPLETED, completedState.getStatus());
        assertEquals(3, completedState.getStepIndex());
        assertEquals("vinekeepers", completedState.get("project"));
    }

    @Test
    void runPromptFlowKeepsSameChannelUsersIsolated() {
        List<Map<String, Object>> steps = List.of(
                Map.of("type", "prompt_for_field", "prompt", "Which project?", "storeIn", "project"),
                Map.of("type", "capture_field", "storeIn", "project"),
                Map.of("type", "done", "message", "Project: {{project}}")
        );
        WorkflowDefinition def = new WorkflowDefinition("conversation", steps);
        ConfigurableWorkflowRunner runner = new ConfigurableWorkflowRunner(def, new WorkflowActionRegistry());
        StateStore store = new StateStore();

        WorkflowRunResult userOnePrompt = runner.runResult(
                new Event("discord:test", "message",
                        Map.of("channelId", "chan-1", "authorId", "user-1", "content", "/Luna")),
                store,
                "luna");
        WorkflowRunResult userTwoPrompt = runner.runResult(
                new Event("discord:test", "message",
                        Map.of("channelId", "chan-1", "authorId", "user-2", "content", "different project")),
                store,
                "luna");

        assertTrue(userOnePrompt.isWaiting());
        assertTrue(userTwoPrompt.isWaiting());
        assertTrue(store.contains("bot:luna:conv:chan-1:user-1"));
        assertTrue(store.contains("bot:luna:conv:chan-1:user-2"));

        WorkflowRunResult userOneResume = runner.runResult(
                new Event("discord:test", "message",
                        Map.of("channelId", "chan-1", "authorId", "user-1", "content", "vinekeepers")),
                store,
                "luna");

        assertTrue(userOneResume.isCompleted());
        assertEquals("Project: vinekeepers", userOneResume.getReplyMessage());
        ConfigurableWorkflowState userTwoState = store.get("bot:luna:conv:chan-1:user-2", ConfigurableWorkflowState.class)
                .orElseThrow();
        assertEquals(ConfigurableWorkflowState.Status.WAITING_INPUT, userTwoState.getStatus());
        assertEquals(1, userTwoState.getStepIndex());
        assertEquals(null, userTwoState.get("project"));
    }

    @Test
    void runResultKeepsSingleEventAskInputBehavior() {
        List<Map<String, Object>> steps = List.of(
                Map.of("type", "ask_input", "prompt", "Say something", "storeIn", "input"),
                Map.of("type", "done", "message", "You said: {{input}}")
        );
        WorkflowDefinition def = new WorkflowDefinition("single-event", steps);
        ConfigurableWorkflowRunner runner = new ConfigurableWorkflowRunner(def, new WorkflowActionRegistry());
        StateStore store = new StateStore();

        WorkflowRunResult result = runner.runResult(
                new Event("test", "message", Map.of("content", "hello")),
                store,
                "bot1");

        assertFalse(result.isWaiting());
        assertTrue(result.isCompleted());
        assertEquals("You said: hello", result.getReplyMessage());
    }

    @Test
    void runPromptForFieldWithPresentChoicesReturnsRichReply() {
        List<Map<String, Object>> steps = List.of(
                Map.<String, Object>of(
                        "type", "prompt_for_field",
                        "prompt", "Which repo?",
                        "storeIn", "repo",
                        "intent", "present_choices",
                        "choices", List.of(
                                Map.of("id", "a", "label", "Repo A", "description", "First"),
                                Map.of("id", "b", "label", "Repo B"))),
                Map.of("type", "capture_field", "storeIn", "repo"),
                Map.of("type", "done", "message", "Selected: {{repo}}")
        );
        WorkflowDefinition def = new WorkflowDefinition("choices", steps);
        ConfigurableWorkflowRunner runner = new ConfigurableWorkflowRunner(def, new WorkflowActionRegistry());
        StateStore store = new StateStore();

        WorkflowRunResult result = runner.runResult(
                new Event("discord:test", "message",
                        Map.of("channelId", "chan-1", "authorId", "user-1", "content", "/start")),
                store,
                "luna");

        assertTrue(result.isWaiting());
        assertTrue(result.getRichReply().isPresent());
        assertEquals("Which repo?", result.getReplyMessage());
        assertEquals("repo", result.getWaitingForField());
    }
}
