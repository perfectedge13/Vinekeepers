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
import com.vinekeepers.interactions.AppReplySink;
import com.vinekeepers.interactions.ChannelTarget;
import com.vinekeepers.interactions.InteractionTarget;
import com.vinekeepers.interactions.OutboundResponse;
import com.vinekeepers.interactions.ReplyTarget;
import com.vinekeepers.workflow.StubWorkflowRunner;
import com.vinekeepers.workflow.WorkflowActionRegistry;
import com.vinekeepers.workflow.WorkflowRunResult;
import com.vinekeepers.workflow.WorkflowRunner;
import com.vinekeepers.workflow.WorkflowRunnerFactory;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.tools.Tool;
import com.vinekeepers.tools.ToolRegistry;
import com.vinekeepers.tools.ToolRunner;
import com.vinekeepers.reasoner.ProposedToolCall;
import com.vinekeepers.reasoner.ReasonerOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
        Map<String, Object> lunaCursorSteps = Map.of(
                "defaultModel", "gpt-4o-mini",
                "steps", List.of(
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
        Map<String, Object> lunaCursorSteps = Map.of(
                "defaultModel", "gpt-4o-mini",
                "steps", List.of(
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

    @Test
    void reasonerAppliesStatePatchRunsToolAndRepliesWhenWorkflowIsSilent() {
        ToolRegistry registry = new ToolRegistry();
        AtomicReference<Map<String, Object>> capturedToolArgs = new AtomicReference<>();
        registry.register(new Tool() {
            @Override
            public String getId() {
                return "echo";
            }

            @Override
            public Object run(Map<String, Object> args) {
                capturedToolArgs.set(args);
                return "tool:" + args.get("message");
            }
        });
        engine = new VinekeepersEngine(router, stateStore, auditLogs::add, new ToolRunner(registry));

        BotDefinition bot = new BotDefinition(
                "reasoner-bot",
                new Persona("Reasoner", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096));
        engine.registerBot(bot);
        engine.registerRunner("reasoner-bot", (event, store, botId) -> WorkflowRunResult.continueWithoutReply());
        engine.registerReasoner("reasoner-bot", input -> new ReasonerOutput(
                "reasoner reply",
                true,
                Map.of("status", "patched"),
                List.of(new ProposedToolCall("echo", Map.of("message", input.getLastUserMessage())))));
        AtomicReference<String> capturedReply = new AtomicReference<>();
        engine.setReplySender((channelId, messageId, content) -> capturedReply.set(content));
        router.addRouting(new Routing(new RoutingFilter(null, null, null, null, null, null), "reasoner-bot"));

        Event event = new Event("discord:default", "message",
                Map.of("channelId", "chan-9", "authorId", "user-1", "content", "ship it"));
        engine.onEvent(event);

        ConfigurableWorkflowState state = stateStore.get("bot:reasoner-bot:conv:chan-9:user-1",
                ConfigurableWorkflowState.class).orElseThrow();
        assertEquals("patched", state.get("status"));
        assertEquals("ship it", capturedToolArgs.get().get("message"));
        assertEquals("reasoner reply", capturedReply.get());
        assertTrue(auditLogs.stream().anyMatch(log ->
                log.getAction().equals("reasoner_tool") && log.getDetail().equals("tool:ship it")));
        assertTrue(auditLogs.stream().anyMatch(log ->
                log.getAction().equals("reasoner") && log.getDetail().equals("reasoner reply")));
    }

    @Test
    void registerSinkReceivesDeliveryWhenEventIsChannelTarget() {
        BotDefinition bot = new BotDefinition(
                "sink-bot",
                new Persona("Sink", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096));
        engine.registerBot(bot);
        AtomicReference<OutboundResponse> capturedResponse = new AtomicReference<>();
        AtomicReference<ReplyTarget> capturedTarget = new AtomicReference<>();
        AppReplySink mockSink = new AppReplySink() {
            @Override
            public void respondImmediately(OutboundResponse response, ReplyTarget target) {
                capturedResponse.set(response);
                capturedTarget.set(target);
            }
            @Override
            public void sendFollowUp(OutboundResponse response, ReplyTarget target) {}
            @Override
            public void updateMessage(OutboundResponse response, ReplyTarget target) {}
            @Override
            public void openModal(OutboundResponse response, ReplyTarget target) {}
            @Override
            public com.vinekeepers.interactions.Capabilities getCapabilities() {
                return new com.vinekeepers.interactions.Capabilities(
                        java.util.Set.of(com.vinekeepers.interactions.ResponseIntentType.PRESENT_CHOICES),
                        true, true, true, false, 3000, 25, 5, 5);
            }
        };
        engine.registerSink("discord", mockSink);
        engine.registerRunner("sink-bot", (event, store, botId) ->
                com.vinekeepers.workflow.WorkflowRunResult.completed(OutboundResponse.ofText("Via sink")));
        engine.registerReasoner("sink-bot", new StubReasoner());
        router.addRouting(new Routing(new RoutingFilter(null, null, null, null, null, null), "sink-bot"));

        Event event = new Event("discord:g:ch", "message",
                Map.of("channelId", "ch-99", "content", "hi"));
        engine.onEvent(event);

        assertNotNull(capturedResponse.get());
        assertEquals("Via sink", capturedResponse.get().getText().orElse(""));
        assertInstanceOf(ChannelTarget.class, capturedTarget.get());
        assertEquals("ch-99", ((ChannelTarget) capturedTarget.get()).channelId());
    }

    @Test
    void whenSinkRegisteredDeferredInteractionUsesSendFollowUp() {
        BotDefinition bot = new BotDefinition(
                "sink-bot",
                new Persona("Sink", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096));
        engine.registerBot(bot);
        AtomicReference<String> calledMethod = new AtomicReference<>();
        AppReplySink mockSink = new AppReplySink() {
            @Override
            public void respondImmediately(OutboundResponse response, ReplyTarget target) {
                calledMethod.set("respondImmediately");
            }
            @Override
            public void sendFollowUp(OutboundResponse response, ReplyTarget target) {
                calledMethod.set("sendFollowUp");
            }
            @Override
            public void updateMessage(OutboundResponse response, ReplyTarget target) {}
            @Override
            public void openModal(OutboundResponse response, ReplyTarget target) {}
            @Override
            public com.vinekeepers.interactions.Capabilities getCapabilities() {
                return new com.vinekeepers.interactions.Capabilities(
                        java.util.Set.of(), true, true, true, false, 3000, 25, 5, 5);
            }
        };
        engine.registerSink("discord", mockSink);
        engine.registerRunner("sink-bot", (event, store, botId) ->
                com.vinekeepers.workflow.WorkflowRunResult.completed(OutboundResponse.ofText("OK")));
        engine.registerReasoner("sink-bot", new StubReasoner());
        router.addRouting(new Routing(new RoutingFilter(null, null, null, null, null, null), "sink-bot"));

        Event event = new Event("discord:g:ch", "interaction",
                Map.of("channelId", "ch-1", "interactionId", "int-1", "token", "tok-1", "deferred", true));
        engine.onEvent(event);

        assertEquals("sendFollowUp", calledMethod.get());
    }

    @Test
    void whenSinkRegisteredRichReplyFromWorkflowIsDeliveredViaSink() {
        BotDefinition bot = new BotDefinition(
                "sink-bot",
                new Persona("Sink", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096));
        engine.registerBot(bot);
        AtomicReference<OutboundResponse> captured = new AtomicReference<>();
        AppReplySink mockSink = new AppReplySink() {
            @Override
            public void respondImmediately(OutboundResponse response, ReplyTarget target) {
                captured.set(response);
            }
            @Override
            public void sendFollowUp(OutboundResponse response, ReplyTarget target) {}
            @Override
            public void updateMessage(OutboundResponse response, ReplyTarget target) {}
            @Override
            public void openModal(OutboundResponse response, ReplyTarget target) {}
            @Override
            public com.vinekeepers.interactions.Capabilities getCapabilities() {
                return new com.vinekeepers.interactions.Capabilities(
                        java.util.Set.of(com.vinekeepers.interactions.ResponseIntentType.PRESENT_CHOICES),
                        true, true, true, false, 3000, 25, 5, 5);
            }
        };
        engine.registerSink("discord", mockSink);
        OutboundResponse rich = OutboundResponse.ofIntent(
                new com.vinekeepers.interactions.PresentChoices("Choose one", java.util.List.of()));
        engine.registerRunner("sink-bot", (event, store, botId) ->
                com.vinekeepers.workflow.WorkflowRunResult.completed(rich));
        engine.registerReasoner("sink-bot", new StubReasoner());
        router.addRouting(new Routing(new RoutingFilter(null, null, null, null, null, null), "sink-bot"));

        Event event = new Event("discord:g:ch", "message", Map.of("channelId", "ch-1"));
        engine.onEvent(event);

        assertNotNull(captured.get());
        assertTrue(captured.get().getIntent().isPresent());
    }

    // --- Discord waiting-session routing (engine adds bots with WAITING_INPUT for event session key) ---

    @Test
    void discordMessageWithoutMentionAndNoWaitingSession_doesNotRouteToLuna() {
        BotDefinition luna = new BotDefinition(
                "luna",
                new Persona("Luna", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096));
        engine.registerBot(luna);
        engine.registerRunner("luna", new StubWorkflowRunner());
        engine.registerReasoner("luna", new StubReasoner());
        router.addRouting(new Routing(
                new RoutingFilter(Set.of(), Set.of(), null, "luna", Set.of(), Set.of(), Set.of()), "luna"));

        Event event = new Event("discord:g:ch", "message",
                Map.of("channelId", "ch-1", "authorId", "user-1", "content", "hello"));
        engine.onEvent(event);

        assertTrue(auditLogs.stream().noneMatch(log -> "luna".equals(log.getBotId())),
                "Luna must not be invoked when message has no mention and no waiting session");
    }

    @Test
    void discordMessageWithMention_routesToLuna() {
        BotDefinition luna = new BotDefinition(
                "luna",
                new Persona("Luna", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096));
        engine.registerBot(luna);
        engine.registerRunner("luna", new StubWorkflowRunner());
        engine.registerReasoner("luna", new StubReasoner());
        router.addRouting(new Routing(
                new RoutingFilter(Set.of(), Set.of(), null, "luna", Set.of(), Set.of(), Set.of()), "luna"));

        Event event = new Event("discord:g:ch", "message",
                Map.of("channelId", "ch-1", "authorId", "user-1", "content", "@Luna help", "mentions", List.of("luna")));
        engine.onEvent(event);

        assertTrue(auditLogs.stream().anyMatch(log -> "luna".equals(log.getBotId())),
                "Initial message with mention must route to Luna");
    }

    @Test
    void followUpMessageWithoutMention_sameUserAndChannel_continuesWorkflowWhenLunaHasWaitingInput() {
        BotDefinition luna = new BotDefinition(
                "luna",
                new Persona("Luna", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096));
        engine.registerBot(luna);
        engine.registerRunner("luna", new StubWorkflowRunner());
        engine.registerReasoner("luna", new StubReasoner());
        router.addRouting(new Routing(
                new RoutingFilter(Set.of(), Set.of(), null, "luna", Set.of(), Set.of(), Set.of()), "luna"));

        String sessionKey = "bot:luna:conv:ch-1:user-1";
        ConfigurableWorkflowState waitingState = new ConfigurableWorkflowState();
        waitingState.markWaiting("project", "Which project?");
        stateStore.put(sessionKey, waitingState);

        Event followUp = new Event("discord:g:ch", "message",
                Map.of("channelId", "ch-1", "authorId", "user-1", "content", "my answer"));
        engine.onEvent(followUp);

        assertTrue(auditLogs.stream().anyMatch(log -> "luna".equals(log.getBotId())),
                "Follow-up without mention from same user/channel must continue workflow when Luna has WAITING_INPUT for that session");
    }

    @Test
    void messageFromDifferentUserOrChannel_doesNotContinueSession() {
        BotDefinition luna = new BotDefinition(
                "luna",
                new Persona("Luna", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096));
        engine.registerBot(luna);
        engine.registerRunner("luna", new StubWorkflowRunner());
        engine.registerReasoner("luna", new StubReasoner());
        router.addRouting(new Routing(
                new RoutingFilter(Set.of(), Set.of(), null, "luna", Set.of(), Set.of(), Set.of()), "luna"));

        String sessionKeyOriginal = "bot:luna:conv:ch-1:user-1";
        ConfigurableWorkflowState waitingState = new ConfigurableWorkflowState();
        waitingState.markWaiting("project", "Which project?");
        stateStore.put(sessionKeyOriginal, waitingState);

        Event differentChannel = new Event("discord:g:ch", "message",
                Map.of("channelId", "ch-2", "authorId", "user-1", "content", "hello"));
        engine.onEvent(differentChannel);

        Event differentUser = new Event("discord:g:ch", "message",
                Map.of("channelId", "ch-1", "authorId", "user-2", "content", "hello"));
        engine.onEvent(differentUser);

        assertTrue(auditLogs.stream().noneMatch(log -> "luna".equals(log.getBotId())),
                "Messages from different user or channel must not continue the waiting session");
    }
}
