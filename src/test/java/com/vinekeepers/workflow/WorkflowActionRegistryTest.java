package com.vinekeepers.workflow;

import com.vinekeepers.events.Event;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowActionRegistryTest {

    @Test
    void registerAndResolve() {
        WorkflowActionRegistry registry = new WorkflowActionRegistry();
        WorkflowAction action = (e, s, b) -> "result";
        registry.register("my_action", action);
        assertTrue(registry.resolve("my_action").isPresent());
        assertEquals(action, registry.resolve("my_action").orElseThrow());
    }

    @Test
    void resolveMissingReturnsEmpty() {
        WorkflowActionRegistry registry = new WorkflowActionRegistry();
        assertTrue(registry.resolve("missing").isEmpty());
    }

    @Test
    void runInvokesActionAndReturnsResult() {
        WorkflowActionRegistry registry = new WorkflowActionRegistry();
        registry.register("echo", (event, state, bind) -> bind != null ? bind.get("msg") : null);
        Event event = new Event("test", "message", Map.of());
        Object out = registry.run("echo", event, Map.of(), Map.of("msg", "hello"));
        assertEquals("hello", out);
    }

    @Test
    void runMissingActionReturnsNull() {
        WorkflowActionRegistry registry = new WorkflowActionRegistry();
        Event event = new Event("test", "message", Map.of());
        assertNull(registry.run("missing", event, Map.of(), Map.of()));
    }

    @Test
    void runThrowingActionRethrowsWithActionContext() {
        WorkflowActionRegistry registry = new WorkflowActionRegistry();
        registry.register("bad", (e, s, b) -> {
            throw new RuntimeException("oops");
        });
        Event event = new Event("test", "message", Map.of());
        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> registry.run("bad", event, Map.of(), Map.of()));
        assertTrue(error.getMessage().contains("Workflow action 'bad' failed: oops"));
    }

    @Test
    void registerIgnoresNullOrBlankId() {
        WorkflowActionRegistry registry = new WorkflowActionRegistry();
        WorkflowAction action = (e, s, b) -> null;
        registry.register(null, action);
        registry.register("", action);
        registry.register("   ", action);
        assertTrue(registry.resolve("valid_id").isEmpty());
    }
}
