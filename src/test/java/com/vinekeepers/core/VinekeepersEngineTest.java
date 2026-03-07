package com.vinekeepers.core;

import com.vinekeepers.audit.AuditLog;
import com.vinekeepers.audit.AuditRecorder;
import com.vinekeepers.bot.BotDefinition;
import com.vinekeepers.bot.MemoryPolicy;
import com.vinekeepers.bot.ModelProfile;
import com.vinekeepers.bot.Persona;
import com.vinekeepers.bot.Router;
import com.vinekeepers.bot.Routing;
import com.vinekeepers.bot.RoutingFilter;
import com.vinekeepers.bot.ToolPolicy;
import com.vinekeepers.events.Event;
import com.vinekeepers.reasoner.StubReasoner;
import com.vinekeepers.state.StateStore;
import com.vinekeepers.connectors.DiscordReplySender;
import com.vinekeepers.workflow.StubWorkflowRunner;
import com.vinekeepers.workflow.WorkflowActionRegistry;
import com.vinekeepers.workflow.WorkflowRunner;
import com.vinekeepers.workflow.WorkflowRunnerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VinekeepersEngineTest {

    private Router router;
    private StateStore stateStore;
    private List<AuditLog> auditLogs;
    private VinekeepersEngine engine;

    @BeforeEach
    void setUp() {
        router = new Router();
        stateStore = new StateStore();
        auditLogs = new ArrayList<>();
        AuditRecorder audit = auditLogs::add;
        engine = new VinekeepersEngine(router, stateStore, audit);
    }

    @Test
    void registerBotAndOnEventRoutesToBot() {
        BotDefinition bot = new BotDefinition(
                "test-bot",
                new Persona("Test", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096));
        engine.registerBot(bot);
        engine.registerRunner("test-bot", new StubWorkflowRunner());
        engine.registerReasoner("test-bot", new StubReasoner());

        router.addRouting(new Routing(new RoutingFilter(null, null, null, null, null, null), "test-bot"));

        Event event = new Event("discord:g:ch", "message", Map.of());
        engine.onEvent(event);

        assertTrue(auditLogs.size() >= 1);
        assertEquals("test-bot", auditLogs.get(0).getBotId());
    }

    @Test
    void onEventWithUnregisteredBotIdDoesNotThrow() {
        router.addRouting(new Routing(new RoutingFilter(null, null, null, null, null, null), "no-such-bot"));
        Event event = new Event("discord:g:ch", "message", Map.of());
        engine.onEvent(event);
        assertTrue(auditLogs.isEmpty());
    }

    @Test
    void lunaWorkflowWithReplySenderSendsReplyToDiscord() {
        Map<String, Object> lunaCursorSteps = Map.of("steps", List.of(
                Map.<String, Object>of("type", "ask_input", "prompt", "Which project do you want to update?", "storeIn", "project"),
                Map.<String, Object>of("type", "ask_input", "prompt", "What code change should I make?", "storeIn", "codeChange"),
                Map.<String, Object>of("type", "call_action", "action", "cursor.fullRun", "bind", Map.of()),
                Map.<String, Object>of("type", "done", "message", "Session complete. Use /Luna again to start.")
        ));
        Map<String, Object> workflows = Map.of("luna_cursor", lunaCursorSteps);
        WorkflowActionRegistry actionRegistry = new WorkflowActionRegistry();
        actionRegistry.register("cursor.fullRun", (e, state, bind) -> "ok");
        WorkflowRunner lunaRunner = WorkflowRunnerFactory.create("configured",
                Map.of("workflowRef", "luna_cursor"), workflows, actionRegistry);

        BotDefinition luna = new BotDefinition(
                "luna",
                new Persona("Luna", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096));
        engine.registerBot(luna);
        engine.registerRunner("luna", lunaRunner);
        engine.registerReasoner("luna", new StubReasoner());
        AtomicReference<String> capturedChannel = new AtomicReference<>();
        AtomicReference<String> capturedContent = new AtomicReference<>();
        DiscordReplySender mockSender = (channelId, messageId, content) -> {
            capturedChannel.set(channelId);
            capturedContent.set(content);
        };
        engine.setReplySender(mockSender);
        router.addRouting(new Routing(new RoutingFilter(null, null, null, null, null, null), "luna"));

        Event event = new Event("discord:default", "message",
                Map.of("channelId", "ch-123", "content", "my-repo"));
        engine.onEvent(event);

        assertNotNull(capturedChannel.get());
        assertEquals("ch-123", capturedChannel.get());
        assertNotNull(capturedContent.get());
        assertTrue(capturedContent.get().contains("Session complete"), "Luna configured workflow should complete and return done message");
    }

    @Test
    void lunaWorkflowPersistsStateByConversationKey() {
        Map<String, Object> lunaCursorSteps = Map.of("steps", List.of(
                Map.<String, Object>of("type", "ask_input", "prompt", "Which project do you want to update?", "storeIn", "project"),
                Map.<String, Object>of("type", "ask_input", "prompt", "What code change should I make?", "storeIn", "codeChange"),
                Map.<String, Object>of("type", "call_action", "action", "cursor.fullRun", "bind", Map.of()),
                Map.<String, Object>of("type", "done", "message", "Session complete. Use /Luna again to start.")
        ));
        Map<String, Object> workflows = Map.of("luna_cursor", lunaCursorSteps);
        WorkflowActionRegistry actionRegistry = new WorkflowActionRegistry();
        actionRegistry.register("cursor.fullRun", (e, state, bind) -> "ok");
        WorkflowRunner lunaRunner = WorkflowRunnerFactory.create("configured",
                Map.of("workflowRef", "luna_cursor"), workflows, actionRegistry);

        BotDefinition luna = new BotDefinition(
                "luna",
                new Persona("Luna", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096));
        engine.registerBot(luna);
        engine.registerRunner("luna", lunaRunner);
        engine.registerReasoner("luna", new StubReasoner());
        router.addRouting(new Routing(new RoutingFilter(null, null, null, null, null, null), "luna"));

        Event first = new Event("discord:default", "message",
                Map.of("channelId", "ch-A", "content", "project-one"));
        engine.onEvent(first);
        Event second = new Event("discord:default", "message",
                Map.of("channelId", "ch-A", "content", "add a test"));
        engine.onEvent(second);

        assertTrue(auditLogs.size() >= 2);
    }
}
