package com.vinekeepers.workflow;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.StateStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void runBranchWithClearClearsStateBeforeAdvancingSoPromptStepReprompts() {
        List<Map<String, Object>> steps = List.of(
                Map.of("type", "prompt_for_field", "prompt", "Which project?", "storeIn", "project"),
                Map.of("type", "capture_field", "storeIn", "project"),
                Map.of("type", "branch", "branches", List.of(
                        Map.of("when", "else", "next", 0, "clear", List.of("project"))))
        );
        WorkflowDefinition def = new WorkflowDefinition("edit-reprompt", steps);
        ConfigurableWorkflowRunner runner = new ConfigurableWorkflowRunner(def, new WorkflowActionRegistry());
        StateStore store = new StateStore();
        String stateKey = "bot:luna:conv:chan-1:user-1";

        WorkflowRunResult first = runner.runResult(
                new Event("discord:test", "message",
                        Map.of("channelId", "chan-1", "authorId", "user-1", "content", "/start")),
                store, "luna");
        assertTrue(first.isWaiting());
        assertEquals("Which project?", first.getReplyMessage());

        WorkflowRunResult afterEdit = runner.runResult(
                new Event("discord:test", "message",
                        Map.of("channelId", "chan-1", "authorId", "user-1", "content", "projA")),
                store, "luna");
        assertTrue(afterEdit.isWaiting());
        assertEquals("Which project?", afterEdit.getReplyMessage());

        ConfigurableWorkflowState state = store.get(stateKey, ConfigurableWorkflowState.class).orElseThrow();
        assertEquals(1, state.getStepIndex());
        assertNull(state.get("project"));
    }

    /**
     * Minimal workflow mirroring luna_cursor confirmation: project → codeChange → confirm (choices) → branch.
     * Branch: launch→8, edit_repo→0 clear [project, codeChange, confirmAction], edit_request→3 clear [codeChange, confirmAction], else→10.
     */
    private static WorkflowDefinition confirmationFlowWithEditBranches() {
        List<Map<String, Object>> steps = List.of(
                Map.of("type", "prompt_for_field", "prompt", "Which project?", "storeIn", "project"),
                Map.of("type", "capture_field", "storeIn", "project"),
                Map.of("type", "branch", "branches", List.of(Map.of("when", "else", "next", 3))),
                Map.of("type", "prompt_for_field", "prompt", "What change?", "storeIn", "codeChange"),
                Map.of("type", "capture_field", "storeIn", "codeChange"),
                Map.<String, Object>of(
                        "type", "prompt_for_field",
                        "prompt", "Confirm?",
                        "storeIn", "confirmAction",
                        "intent", "present_choices",
                        "choices", List.of(
                                Map.of("id", "launch", "label", "Launch"),
                                Map.of("id", "edit_repo", "label", "Edit repo"),
                                Map.of("id", "edit_request", "label", "Edit request"),
                                Map.of("id", "cancel", "label", "Cancel"))),
                Map.of("type", "capture_field", "storeIn", "confirmAction"),
                Map.of("type", "branch", "branches", List.of(
                        Map.of("when", Map.of("key", "confirmAction", "value", "launch"), "next", 8),
                        Map.of("when", Map.of("key", "confirmAction", "value", "edit_repo"), "next", 0, "clear", List.of("project", "codeChange", "confirmAction")),
                        Map.of("when", Map.of("key", "confirmAction", "value", "edit_request"), "next", 3, "clear", List.of("codeChange", "confirmAction")),
                        Map.of("when", "else", "next", 10))),
                Map.of("type", "done", "message", "Launched"),
                Map.of("type", "done", "message", "unused"),
                Map.of("type", "done", "message", "Cancelled")
        );
        return new WorkflowDefinition("confirm-edit", steps);
    }

    @Test
    void editRequestClearsConfirmActionSoConfirmationShowsAgainAndLaunchWorks() {
        ConfigurableWorkflowRunner runner = new ConfigurableWorkflowRunner(
                confirmationFlowWithEditBranches(), new WorkflowActionRegistry());
        StateStore store = new StateStore();
        String stateKey = "bot:luna:conv:chan-1:user-1";

        // 1: Start → waiting for project
        WorkflowRunResult r1 = runner.runResult(
                new Event("discord:test", "message", Map.of("channelId", "chan-1", "authorId", "user-1", "content", "/start")),
                store, "luna");
        assertTrue(r1.isWaiting());
        assertEquals("Which project?", r1.getReplyMessage());

        // 2: Send project → waiting for codeChange
        WorkflowRunResult r2 = runner.runResult(
                new Event("discord:test", "message", Map.of("channelId", "chan-1", "authorId", "user-1", "content", "my-repo")),
                store, "luna");
        assertTrue(r2.isWaiting());
        assertEquals("What change?", r2.getReplyMessage());

        // 3: Send request → waiting for confirm
        WorkflowRunResult r3 = runner.runResult(
                new Event("discord:test", "message", Map.of("channelId", "chan-1", "authorId", "user-1", "content", "add feature X")),
                store, "luna");
        assertTrue(r3.isWaiting());
        assertEquals("Confirm?", r3.getReplyMessage());

        // 4: Click Edit request (interaction)
        WorkflowRunResult r4 = runner.runResult(
                new Event("discord:test", "interaction", Map.of("channelId", "chan-1", "authorId", "user-1", "customId", "edit_request", "values", List.of("edit_request"))),
                store, "luna");
        assertTrue(r4.isWaiting());
        assertEquals("What change?", r4.getReplyMessage());
        ConfigurableWorkflowState afterEdit = store.get(stateKey, ConfigurableWorkflowState.class).orElseThrow();
        assertNull(afterEdit.get("codeChange"));
        assertNull(afterEdit.get("confirmAction"));

        // 5: Send new request → waiting for confirm again
        WorkflowRunResult r5 = runner.runResult(
                new Event("discord:test", "message", Map.of("channelId", "chan-1", "authorId", "user-1", "content", "new request text")),
                store, "luna");
        assertTrue(r5.isWaiting());
        assertEquals("Confirm?", r5.getReplyMessage());
        assertEquals("new request text", store.get(stateKey, ConfigurableWorkflowState.class).orElseThrow().get("codeChange"));

        // 6: Click Launch → completed
        WorkflowRunResult r6 = runner.runResult(
                new Event("discord:test", "interaction", Map.of("channelId", "chan-1", "authorId", "user-1", "customId", "launch", "values", List.of("launch"))),
                store, "luna");
        assertFalse(r6.isWaiting());
        assertTrue(r6.isCompleted());
        assertEquals("Launched", r6.getReplyMessage());
    }

    @Test
    void editRepoClearsProjectCodeChangeConfirmActionSoRepoAndRequestRepromptAndLaunchWorks() {
        ConfigurableWorkflowRunner runner = new ConfigurableWorkflowRunner(
                confirmationFlowWithEditBranches(), new WorkflowActionRegistry());
        StateStore store = new StateStore();
        String stateKey = "bot:luna:conv:chan-1:user-1";

        // 1–3: Get to confirmation (same as above)
        runner.runResult(new Event("discord:test", "message", Map.of("channelId", "chan-1", "authorId", "user-1", "content", "/start")), store, "luna");
        runner.runResult(new Event("discord:test", "message", Map.of("channelId", "chan-1", "authorId", "user-1", "content", "repo-A")), store, "luna");
        runner.runResult(new Event("discord:test", "message", Map.of("channelId", "chan-1", "authorId", "user-1", "content", "first request")), store, "luna");

        // 4: Click Edit repo
        WorkflowRunResult r4 = runner.runResult(
                new Event("discord:test", "interaction", Map.of("channelId", "chan-1", "authorId", "user-1", "customId", "edit_repo", "values", List.of("edit_repo"))),
                store, "luna");
        assertTrue(r4.isWaiting());
        assertEquals("Which project?", r4.getReplyMessage());
        ConfigurableWorkflowState afterEdit = store.get(stateKey, ConfigurableWorkflowState.class).orElseThrow();
        assertNull(afterEdit.get("project"));
        assertNull(afterEdit.get("codeChange"));
        assertNull(afterEdit.get("confirmAction"));

        // 5: Send new project → waiting for codeChange
        WorkflowRunResult r5 = runner.runResult(
                new Event("discord:test", "message", Map.of("channelId", "chan-1", "authorId", "user-1", "content", "repo-B")),
                store, "luna");
        assertTrue(r5.isWaiting());
        assertEquals("What change?", r5.getReplyMessage());

        // 6: Send request → waiting for confirm
        WorkflowRunResult r6 = runner.runResult(
                new Event("discord:test", "message", Map.of("channelId", "chan-1", "authorId", "user-1", "content", "second request")),
                store, "luna");
        assertTrue(r6.isWaiting());
        assertEquals("Confirm?", r6.getReplyMessage());

        // 7: Launch → completed
        WorkflowRunResult r7 = runner.runResult(
                new Event("discord:test", "interaction", Map.of("channelId", "chan-1", "authorId", "user-1", "customId", "launch", "values", List.of("launch"))),
                store, "luna");
        assertTrue(r7.isCompleted());
        assertEquals("Launched", r7.getReplyMessage());
        ConfigurableWorkflowState finalState = store.get(stateKey, ConfigurableWorkflowState.class).orElseThrow();
        assertEquals("repo-B", finalState.get("project"));
        assertEquals("second request", finalState.get("codeChange"));
    }

    @Test
    void runCaptureFieldWithTrimAndLowerStoresTrimmedAndLowercasedValue() {
        List<Map<String, Object>> steps = List.of(
                Map.of("type", "ask_input", "prompt", "Room name?", "storeIn", "room"),
                Map.of("type", "capture_field", "storeIn", "room", "trimAndLower", true),
                Map.of("type", "done", "message", "Room: {{room}}")
        );
        WorkflowDefinition def = new WorkflowDefinition("arrietty_room", steps);
        ConfigurableWorkflowRunner runner = new ConfigurableWorkflowRunner(def, new WorkflowActionRegistry());
        StateStore store = new StateStore();

        WorkflowRunResult result = runner.runResult(
                new Event("test", "message", Map.of("content", "  Arrietty Room  ")),
                store,
                "arrietty");

        assertFalse(result.isWaiting());
        assertTrue(result.isCompleted());
        assertEquals("Room: arrietty room", result.getReplyMessage());
        ConfigurableWorkflowState state = store.get("bot:arrietty:state", ConfigurableWorkflowState.class).orElseThrow();
        assertEquals("arrietty room", state.get("room"));
    }

    @Test
    void configuredCallActionStepModelOverridesStateAndBindAtRuntime() {
        WorkflowActionRegistry registry = new WorkflowActionRegistry();
        registry.register("launch_cursor_run", (event, state, bind) -> bind.get("model"));
        List<Map<String, Object>> steps = List.of(
                Map.of("type", "call_action", "action", "launch_cursor_run", "model", "gpt-4.1-mini", "storeIn", "usedModel"),
                Map.of("type", "done", "message", "Model: {{usedModel}}")
        );
        WorkflowDefinition def = new WorkflowDefinition("model-flow", steps);
        ConfigurableWorkflowRunner runner = new ConfigurableWorkflowRunner(
                def,
                registry,
                null,
                null,
                null,
                null,
                null,
                "gpt-4o-mini",
                Set.of("gpt-4.1-mini", "gpt-4o-mini"));
        StateStore store = new StateStore();
        String key = "bot:luna:state";
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        state.put("model", "state-model");
        store.put(key, state);

        String out = runner.run(new Event("test", "message", Map.of()), store, "luna");
        assertEquals("Model: gpt-4.1-mini", out);
    }

    @Test
    void configuredCallActionStepWithoutModelUsesDefaultModel() {
        WorkflowActionRegistry registry = new WorkflowActionRegistry();
        registry.register("launch_cursor_run", (event, state, bind) -> bind.get("model"));
        List<Map<String, Object>> steps = List.of(
                Map.of("type", "call_action", "action", "launch_cursor_run", "storeIn", "usedModel"),
                Map.of("type", "done", "message", "Model: {{usedModel}}")
        );
        WorkflowDefinition def = new WorkflowDefinition("default-model-flow", steps);
        ConfigurableWorkflowRunner runner = new ConfigurableWorkflowRunner(
                def, registry, null, null, null, null, null, "gpt-4o-mini", Set.of("gpt-4o-mini"));
        String out = runner.run(new Event("test", "message", Map.of()), new StateStore(), "luna");
        assertEquals("Model: gpt-4o-mini", out);
    }

    @Test
    void configuredCallActionStepRejectsUnknownModel() {
        WorkflowActionRegistry registry = new WorkflowActionRegistry();
        List<Map<String, Object>> steps = List.of(
                Map.of("type", "call_action", "action", "launch_cursor_run", "model", "unknown-model")
        );
        WorkflowDefinition def = new WorkflowDefinition("invalid-model", steps);
        assertThrows(IllegalArgumentException.class, () -> new ConfigurableWorkflowRunner(
                def, registry, null, null, null, null, null, "gpt-4o-mini", Set.of("gpt-4o-mini")));
    }

    @Test
    void nonLlmStepWithModelFailsValidation() {
        WorkflowActionRegistry registry = new WorkflowActionRegistry();
        List<Map<String, Object>> steps = List.of(
                Map.of("type", "ask_input", "prompt", "x", "storeIn", "input", "model", "gpt-4.1-mini")
        );
        WorkflowDefinition def = new WorkflowDefinition("invalid-step-model", steps);
        assertThrows(IllegalArgumentException.class, () -> new ConfigurableWorkflowRunner(
                def, registry, null, null, null, null, null, "gpt-4o-mini", Set.of("gpt-4o-mini", "gpt-4.1-mini")));
    }
}
