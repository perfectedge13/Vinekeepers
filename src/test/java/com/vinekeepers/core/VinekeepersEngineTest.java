package com.vinekeepers.core;

import com.vinekeepers.audit.AuditLog;
import com.vinekeepers.audit.AuditRecorder;
import com.vinekeepers.bot.BotDefinition;
import com.vinekeepers.bot.ConversationMode;
import com.vinekeepers.bot.MemoryPolicy;
import com.vinekeepers.bot.ModelProfile;
import com.vinekeepers.bot.Persona;
import com.vinekeepers.bot.Router;
import com.vinekeepers.bot.RoutingRule;
import com.vinekeepers.bot.RoutingFilter;
import com.vinekeepers.bot.ToolPolicy;
import com.vinekeepers.events.Event;
import com.vinekeepers.reasoner.StubReasoner;
import com.vinekeepers.state.LifecycleContextStore;
import com.vinekeepers.state.StateStore;
import com.vinekeepers.state.planning.FeatureRoomState;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.planning.PlanningRole;
import com.vinekeepers.state.planning.RoomParticipant;
import com.vinekeepers.connectors.DiscordReplyTargetResolver;
import com.vinekeepers.connectors.ReplySender;
import com.vinekeepers.connectors.ReplyTargetResolver;
import com.vinekeepers.interactions.AppReplySink;
import com.vinekeepers.interactions.ChannelTarget;
import com.vinekeepers.interactions.OutboundResponse;
import com.vinekeepers.interactions.ReplyTarget;
import com.vinekeepers.workflow.actions.CoordinatorIntakeBootstrapAction;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

        router.addRouting(new RoutingRule(new RoutingFilter(null, null, null, null, null, null), "test-bot"));

        Event event = new Event("discord:g:ch", "message", Map.of());
        engine.onEvent(event);

        assertTrue(auditLogs.size() >= 1);
        assertEquals("test-bot", auditLogs.get(0).getBotId());
    }

    @Test
    void onEventWithUnregisteredBotIdDoesNotThrow() {
        router.addRouting(new RoutingRule(new RoutingFilter(null, null, null, null, null, null), "no-such-bot"));
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
        ReplySender mockSender = (channelId, messageId, content) -> {
            capturedChannel.set(channelId);
            capturedContent.set(content);
        };
        engine.setReplySender(mockSender);
        engine.registerReplyTargetResolver("discord", new DiscordReplyTargetResolver());
        router.addRouting(new RoutingRule(new RoutingFilter(null, null, null, null, null, null), "luna"));

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
        router.addRouting(new RoutingRule(new RoutingFilter(null, null, null, null, null, null), "luna"));

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
        engine.registerReplyTargetResolver("discord", new DiscordReplyTargetResolver());
        router.addRouting(new RoutingRule(new RoutingFilter(null, null, null, null, null, null), "reasoner-bot"));

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
        engine.registerReplyTargetResolver("discord", new DiscordReplyTargetResolver());
        engine.registerRunner("sink-bot", (event, store, botId) ->
                com.vinekeepers.workflow.WorkflowRunResult.completed(OutboundResponse.ofText("Via sink")));
        engine.registerReasoner("sink-bot", new StubReasoner());
        router.addRouting(new RoutingRule(new RoutingFilter(null, null, null, null, null, null), "sink-bot"));

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
        engine.registerReplyTargetResolver("discord", new DiscordReplyTargetResolver());
        engine.registerRunner("sink-bot", (event, store, botId) ->
                com.vinekeepers.workflow.WorkflowRunResult.completed(OutboundResponse.ofText("OK")));
        engine.registerReasoner("sink-bot", new StubReasoner());
        router.addRouting(new RoutingRule(new RoutingFilter(null, null, null, null, null, null), "sink-bot"));

        Event event = new Event("discord:g:ch", "interaction",
                Map.of("channelId", "ch-1", "interactionId", "int-1", "token", "tok-1", "deferred", true));
        engine.onEvent(event);

        assertEquals("sendFollowUp", calledMethod.get());
    }

    @Test
    void deferredInteractionWithNullOutbound_sendsMinimalFollowUpAck() {
        BotDefinition bot = new BotDefinition(
                "sink-bot",
                new Persona("Sink", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096));
        engine.registerBot(bot);
        AtomicReference<OutboundResponse> followUpPayload = new AtomicReference<>();
        AppReplySink mockSink = new AppReplySink() {
            @Override
            public void respondImmediately(OutboundResponse response, ReplyTarget target) {
            }
            @Override
            public void sendFollowUp(OutboundResponse response, ReplyTarget target) {
                followUpPayload.set(response);
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
        engine.registerReplyTargetResolver("discord", new DiscordReplyTargetResolver());
        engine.registerRunner("sink-bot", (event, store, botId) -> WorkflowRunResult.continueWithoutReply());
        engine.registerReasoner("sink-bot", new StubReasoner());
        router.addRouting(new RoutingRule(new RoutingFilter(null, null, null, null, null, null), "sink-bot"));

        Event event = new Event("discord:g:ch", "interaction",
                Map.of("channelId", "ch-1", "interactionId", "int-1", "token", "tok-1", "deferred", true));
        engine.onEvent(event);

        assertNotNull(followUpPayload.get());
        assertEquals("Recorded.", followUpPayload.get().getText().orElse(""));
    }

    @Test
    void deferredInteractionWithNullOutbound_arriettyRoomUsesPlanningAckText() {
        BotDefinition bot = new BotDefinition(
                "coordinator-bot",
                new Persona("Arrietty", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096),
                "configured",
                java.util.Map.of("workflowRef", "arrietty_room_v2"));
        engine.registerBot(bot);
        AtomicReference<OutboundResponse> followUpPayload = new AtomicReference<>();
        AppReplySink mockSink = new AppReplySink() {
            @Override
            public void respondImmediately(OutboundResponse response, ReplyTarget target) {
            }
            @Override
            public void sendFollowUp(OutboundResponse response, ReplyTarget target) {
                followUpPayload.set(response);
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
        engine.registerReplyTargetResolver("discord", new DiscordReplyTargetResolver());
        engine.registerRunner("coordinator-bot", (event, store, botId) -> WorkflowRunResult.continueWithoutReply());
        engine.registerReasoner("coordinator-bot", new StubReasoner());
        router.addRouting(new RoutingRule(new RoutingFilter(null, null, null, null, null, null), "coordinator-bot"));

        Event event = new Event("discord:g:ch", "interaction",
                Map.of("channelId", "ch-1", "interactionId", "int-1", "token", "tok-1", "deferred", true));
        engine.onEvent(event);

        assertNotNull(followUpPayload.get());
        assertEquals("Working on your update…", followUpPayload.get().getText().orElse(""));
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
        engine.registerReplyTargetResolver("discord", new DiscordReplyTargetResolver());
        OutboundResponse rich = OutboundResponse.ofIntent(
                new com.vinekeepers.interactions.PresentChoices("Choose one", java.util.List.of()));
        engine.registerRunner("sink-bot", (event, store, botId) ->
                com.vinekeepers.workflow.WorkflowRunResult.completed(rich));
        engine.registerReasoner("sink-bot", new StubReasoner());
        router.addRouting(new RoutingRule(new RoutingFilter(null, null, null, null, null, null), "sink-bot"));

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
        router.addRouting(new RoutingRule(
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
        router.addRouting(new RoutingRule(
                new RoutingFilter(Set.of(), Set.of(), null, "luna", Set.of(), Set.of(), Set.of()), "luna"));

        Event event = new Event("discord:g:ch", "message",
                Map.of("channelId", "ch-1", "authorId", "user-1", "content", "@Luna help", "mentions", List.of("luna")));
        engine.onEvent(event);

        assertTrue(auditLogs.stream().anyMatch(log -> "luna".equals(log.getBotId())),
                "Initial message with mention must route to Luna");
    }

    @Test
    void duplicateDiscordMessageId_withinDedupeWindow_runsWorkflowOnce() {
        BotDefinition luna = new BotDefinition(
                "luna",
                new Persona("Luna", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096));
        engine.registerBot(luna);
        AtomicInteger runs = new AtomicInteger();
        engine.registerRunner("luna", (event, stateStore, botId) -> {
            runs.incrementAndGet();
            return WorkflowRunResult.continueWithoutReply();
        });
        engine.registerReasoner("luna", new StubReasoner());
        router.addRouting(new RoutingRule(
                new RoutingFilter(Set.of(), Set.of(), null, "luna", Set.of(), Set.of(), Set.of()), "luna"));

        Event event = new Event("discord:guildX", "message", Map.of(
                "channelId", "ch-1",
                "authorId", "user-1",
                "messageId", "snowflake-duplicate-test-001",
                "content", "@Luna hi",
                "mentions", List.of("luna")));
        engine.onEvent(event);
        engine.onEvent(event);

        assertEquals(1, runs.get(), "Duplicate Discord message publication must not advance workflow twice");
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
        router.addRouting(new RoutingRule(
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
        router.addRouting(new RoutingRule(
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

    // --- Connector-keyed reply sender (fallback when no sink) ---

    @Test
    void fallbackUsesReplySenderRegisteredByConnectorId() {
        BotDefinition bot = new BotDefinition(
                "bot",
                new Persona("Bot", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096));
        engine.registerBot(bot);
        engine.registerRunner("bot", (event, store, botId) ->
                WorkflowRunResult.completed(OutboundResponse.ofText("reply text")));
        engine.registerReasoner("bot", new StubReasoner());
        router.addRouting(new RoutingRule(new RoutingFilter(null, null, null, null, null, null), "bot"));

        AtomicReference<String> sentChannel = new AtomicReference<>();
        AtomicReference<String> sentContent = new AtomicReference<>();
        ReplySender discordSender = (channelId, messageId, content) -> {
            sentChannel.set(channelId);
            sentContent.set(content);
        };
        engine.setReplySender("discord", discordSender);
        engine.registerReplyTargetResolver("discord", new DiscordReplyTargetResolver());
        // No sink registered — fallback to reply sender by source prefix "discord"

        Event event = new Event("discord:g:ch", "message",
                Map.of("channelId", "ch-1", "messageId", "msg-1", "content", "hi"));
        engine.onEvent(event);

        assertEquals("ch-1", sentChannel.get());
        assertEquals("reply text", sentContent.get());
    }

    @Test
    void setReplySenderWithoutConnectorIdUsesDiscordFallback() {
        BotDefinition bot = new BotDefinition(
                "bot",
                new Persona("Bot", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096));
        engine.registerBot(bot);
        engine.registerRunner("bot", (event, store, botId) ->
                WorkflowRunResult.completed(OutboundResponse.ofText("legacy reply")));
        engine.registerReasoner("bot", new StubReasoner());
        router.addRouting(new RoutingRule(new RoutingFilter(null, null, null, null, null, null), "bot"));

        AtomicReference<String> sentContent = new AtomicReference<>();
        ReplySender sender = (channelId, messageId, content) -> sentContent.set(content);
        engine.setReplySender(sender); // backward compat: registers under "discord"
        engine.registerReplyTargetResolver("discord", new DiscordReplyTargetResolver());

        Event event = new Event("discord:g:ch", "message",
                Map.of("channelId", "ch-2", "content", "hi"));
        engine.onEvent(event);

        assertEquals("legacy reply", sentContent.get());
    }

    @Test
    void resolverRegisteredForConnectorIdIsUsedForTarget() {
        BotDefinition bot = new BotDefinition(
                "bot",
                new Persona("Bot", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096));
        engine.registerBot(bot);
        engine.registerRunner("bot", (event, store, botId) ->
                WorkflowRunResult.completed(OutboundResponse.ofText("reply")));
        engine.registerReasoner("bot", new StubReasoner());
        router.addRouting(new RoutingRule(new RoutingFilter(null, null, null, null, null, null), "bot"));

        ReplyTarget resolvedTarget = new ChannelTarget("discord:g:ch", "resolved-ch", "resolved-msg");
        ReplyTargetResolver mockResolver = event -> java.util.Optional.of(resolvedTarget);
        engine.registerReplyTargetResolver("discord", mockResolver);

        AtomicReference<String> sentChannel = new AtomicReference<>();
        AtomicReference<String> sentMessageId = new AtomicReference<>();
        ReplySender sender = (channelId, messageId, content) -> {
            sentChannel.set(channelId);
            sentMessageId.set(messageId);
        };
        engine.setReplySender("discord", sender);

        Event event = new Event("discord:g:ch", "message",
                Map.of("channelId", "payload-ch", "content", "hi"));
        engine.onEvent(event);

        assertEquals("resolved-ch", sentChannel.get());
        assertEquals("resolved-msg", sentMessageId.get());
    }

    @Test
    void whenNoResolverRegistered_doesNotDeliverReply() {
        BotDefinition bot = new BotDefinition(
                "bot",
                new Persona("Bot", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096));
        engine.registerBot(bot);
        engine.registerRunner("bot", (event, store, botId) ->
                WorkflowRunResult.completed(OutboundResponse.ofText("reply")));
        engine.registerReasoner("bot", new StubReasoner());
        router.addRouting(new RoutingRule(new RoutingFilter(null, null, null, null, null, null), "bot"));
        AtomicReference<String> sentChannel = new AtomicReference<>();
        AtomicReference<String> sentMessageId = new AtomicReference<>();
        engine.setReplySender("discord", (channelId, messageId, content) -> {
            sentChannel.set(channelId);
            sentMessageId.set(messageId);
        });
        // No resolver registered for "discord" — engine must not deliver (fail closed)

        Event event = new Event("discord:g:ch", "message",
                Map.of("channelId", "ch-1", "messageId", "msg-1", "content", "hi"));
        engine.onEvent(event);

        assertEquals(null, sentChannel.get());
        assertEquals(null, sentMessageId.get());
    }

    @Test
    void whenResolverReturnsEmpty_doesNotDeliverReply() {
        BotDefinition bot = new BotDefinition(
                "bot",
                new Persona("Bot", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096));
        engine.registerBot(bot);
        engine.registerRunner("bot", (event, store, botId) ->
                WorkflowRunResult.completed(OutboundResponse.ofText("reply")));
        engine.registerReasoner("bot", new StubReasoner());
        router.addRouting(new RoutingRule(new RoutingFilter(null, null, null, null, null, null), "bot"));
        ReplyTargetResolver emptyResolver = event -> java.util.Optional.empty();
        engine.registerReplyTargetResolver("discord", emptyResolver);
        AtomicReference<String> sentChannel = new AtomicReference<>();
        AtomicReference<String> sentMessageId = new AtomicReference<>();
        engine.setReplySender("discord", (channelId, messageId, content) -> {
            sentChannel.set(channelId);
            sentMessageId.set(messageId);
        });

        Event event = new Event("discord:g:ch", "message",
                Map.of("channelId", "ch-1", "messageId", "msg-1", "content", "hi"));
        engine.onEvent(event);

        assertEquals(null, sentChannel.get());
        assertEquals(null, sentMessageId.get());
    }

    @Test
    void whenNoSenderForSourcePrefixSendIsNotCalled() {
        BotDefinition bot = new BotDefinition(
                "bot",
                new Persona("Bot", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096));
        engine.registerBot(bot);
        engine.registerRunner("bot", (event, store, botId) ->
                WorkflowRunResult.completed(OutboundResponse.ofText("reply")));
        engine.registerReasoner("bot", new StubReasoner());
        router.addRouting(new RoutingRule(new RoutingFilter(null, null, null, null, null, null), "bot"));

        ReplySender discordOnlySender = (channelId, messageId, content) -> {
            throw new AssertionError("Sender must not be called for unknown connector");
        };
        engine.setReplySender("discord", discordOnlySender);
        // Event from "other" connector — no sender registered for "other"

        Event event = new Event("other:g:ch", "message",
                Map.of("channelId", "ch-1", "content", "hi"));
        engine.onEvent(event);

        // No exception and audit shows bot was handled (reply simply not delivered)
        assertTrue(auditLogs.stream().anyMatch(log -> "bot".equals(log.getBotId())));
    }

    @Test
    void dispatchCoordinatorPlanningKickoff_returnsNoBotWhenMissing() {
        Event syn = new Event("discord:t", "message", Map.of("channelId", "th1", "threadId", "th1"));
        assertEquals("NO_BOT", engine.dispatchCoordinatorPlanningKickoff(syn, ""));
        assertEquals("NO_BOT", engine.dispatchCoordinatorPlanningKickoff(syn, "nope"));
    }

    @Test
    void dispatchCoordinatorPlanningKickoff_secondInvokeSkippedWhenWaiting() {
        WorkflowActionRegistry actionRegistry = new WorkflowActionRegistry();
        Map<String, Object> wf = Map.of("steps", List.of(
                Map.of("type", "prompt_for_field", "prompt", "Wait here", "storeIn", "fld"),
                Map.of("type", "capture_field", "storeIn", "fld"),
                Map.of("type", "done", "message", "ok")));
        Map<String, Object> workflows = Map.of("kick_wait", wf);
        BotDefinition arrietty = new BotDefinition(
                "arrietty",
                new Persona("A", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096),
                "configured",
                Map.of("workflowRef", "kick_wait"),
                ConversationMode.CONVERSATIONAL,
                "thread",
                null);
        engine.registerBot(arrietty);
        engine.registerReasoner("arrietty", new StubReasoner());
        engine.registerRunner("arrietty", WorkflowRunnerFactory.create(arrietty, workflows, actionRegistry,
                new ToolRunner(new ToolRegistry())));
        engine.setReplySender("discord", (c, m, t) -> { });
        engine.registerReplyTargetResolver("discord", new DiscordReplyTargetResolver());

        Event syn = new Event("discord:g:1", "message", Map.of("channelId", "thread-xyz", "threadId", "thread-xyz"));
        assertEquals("RAN", engine.dispatchCoordinatorPlanningKickoff(syn, "arrietty"));
        assertEquals("SKIPPED_WAITING", engine.dispatchCoordinatorPlanningKickoff(syn, "arrietty"));
    }

    @Test
    void dispatchCoordinatorPlanningKickoff_thenThreadUserMessageContinuesSession() {
        WorkflowActionRegistry actionRegistry = new WorkflowActionRegistry();
        Map<String, Object> wf = Map.of("steps", List.of(
                Map.of("type", "prompt_for_field", "prompt", "prompt", "storeIn", "answerField"),
                Map.of("type", "capture_field", "storeIn", "answerField"),
                Map.of("type", "done", "message", "done-{{answerField}}")));
        Map<String, Object> workflows = Map.of("kick_continue", wf);
        BotDefinition arrietty = new BotDefinition(
                "arrietty",
                new Persona("A", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096),
                "configured",
                Map.of("workflowRef", "kick_continue"),
                ConversationMode.CONVERSATIONAL,
                "thread",
                null);
        engine.registerBot(arrietty);
        engine.registerReasoner("arrietty", new StubReasoner());
        engine.registerRunner("arrietty", WorkflowRunnerFactory.create(arrietty, workflows, actionRegistry,
                new ToolRunner(new ToolRegistry())));
        AtomicReference<String> lastSend = new AtomicReference<>();
        engine.setReplySender("discord", (c, m, t) -> lastSend.set(t));
        engine.registerReplyTargetResolver("discord", new DiscordReplyTargetResolver());
        router.addRouting(new RoutingRule(new RoutingFilter(null, null, null, null, null, null), "arrietty"));

        String tid = "thread-same";
        Event syn = new Event("discord:g:1", "message", Map.of("channelId", tid, "threadId", tid));
        assertEquals("RAN", engine.dispatchCoordinatorPlanningKickoff(syn, "arrietty"));

        Event user = new Event("discord:g:1", "message",
                Map.of("channelId", tid, "threadId", tid, "authorId", "u1", "content", "my answer"));
        engine.onEvent(user);

        assertEquals("done-my answer", lastSend.get());
    }

    @Test
    void dispatchCoordinatorPlanningKickoff_blankErrorPostsFallbackAndReturnsRanError() {
        BotDefinition arrietty = new BotDefinition(
                "arrietty",
                new Persona("A", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096),
                "configured",
                Map.of("workflowRef", "arrietty_room_v2"),
                ConversationMode.CONVERSATIONAL,
                "thread",
                null);
        engine.registerBot(arrietty);
        engine.registerRunner("arrietty", (event, store, botId) -> WorkflowRunResult.error(null));
        engine.registerReasoner("arrietty", new StubReasoner());
        AtomicReference<String> sent = new AtomicReference<>();
        engine.setReplySender("discord", (channelId, messageId, content) -> sent.set(content));
        engine.registerReplyTargetResolver("discord", new DiscordReplyTargetResolver());

        String threadId = "thread-kickoff-error";
        Event syn = new Event("discord:g:1", "message", Map.of("channelId", threadId, "threadId", threadId));
        assertEquals("RAN_ERROR", engine.dispatchCoordinatorPlanningKickoff(syn, "arrietty"));
        assertEquals("Planning hit an internal error during kickoff. See server logs.", sent.get());

        String sessionKey = "bot:arrietty:conv:" + threadId;
        ConfigurableWorkflowState state = stateStore.get(sessionKey, ConfigurableWorkflowState.class).orElseThrow();
        assertEquals(ConfigurableWorkflowState.Status.ERROR, state.getStatus());
        assertTrue(String.valueOf(state.get("coordinatorKickoffFailureReason")).contains("failed"));
    }

    @Test
    void dispatchCoordinatorPlanningKickoff_blankCompletedReplyPostsFallbackAndReturnsRanNoOutbound() {
        BotDefinition arrietty = new BotDefinition(
                "arrietty",
                new Persona("A", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096),
                "configured",
                Map.of("workflowRef", "arrietty_room_v2"),
                ConversationMode.CONVERSATIONAL,
                "thread",
                null);
        engine.registerBot(arrietty);
        engine.registerRunner("arrietty", (event, store, botId) -> WorkflowRunResult.completed(""));
        engine.registerReasoner("arrietty", new StubReasoner());
        AtomicReference<String> sent = new AtomicReference<>();
        engine.setReplySender("discord", (channelId, messageId, content) -> sent.set(content));
        engine.registerReplyTargetResolver("discord", new DiscordReplyTargetResolver());

        String threadId = "thread-kickoff-empty";
        Event syn = new Event("discord:g:1", "message", Map.of("channelId", threadId, "threadId", threadId));
        assertEquals("RAN_NO_OUTBOUND", engine.dispatchCoordinatorPlanningKickoff(syn, "arrietty"));
        assertEquals("Planning hit an internal error during kickoff. See server logs.", sent.get());

        String sessionKey = "bot:arrietty:conv:" + threadId;
        ConfigurableWorkflowState state = stateStore.get(sessionKey, ConfigurableWorkflowState.class).orElseThrow();
        assertEquals(ConfigurableWorkflowState.Status.ERROR, state.getStatus());
        assertTrue(String.valueOf(state.get("coordinatorKickoffFailureReason")).contains("no visible reply"));
    }

    @Test
    void dispatchCoordinatorPlanningKickoff_visibleWorkflowSideEffectSkipsFallback() {
        BotDefinition arrietty = new BotDefinition(
                "arrietty",
                new Persona("A", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096),
                "configured",
                Map.of("workflowRef", "arrietty_room_v2"),
                ConversationMode.CONVERSATIONAL,
                "thread",
                null);
        engine.registerBot(arrietty);
        engine.registerRunner("arrietty", (event, store, botId) -> {
            String stateKey = "bot:" + botId + ":conv:" + event.getPayload().get("threadId");
            ConfigurableWorkflowState kickoffState =
                    store.get(stateKey, ConfigurableWorkflowState.class).orElseGet(ConfigurableWorkflowState::new);
            kickoffState.put(CoordinatorIntakeBootstrapAction.KICKOFF_VISIBLE_OUTCOME_KEY, "true");
            store.put(stateKey, kickoffState);
            return WorkflowRunResult.completed("");
        });
        engine.registerReasoner("arrietty", new StubReasoner());
        AtomicReference<String> sent = new AtomicReference<>();
        engine.setReplySender("discord", (channelId, messageId, content) -> sent.set(content));
        engine.registerReplyTargetResolver("discord", new DiscordReplyTargetResolver());

        String threadId = "thread-kickoff-visible";
        Event syn = new Event("discord:g:1", "message", Map.of("channelId", threadId, "threadId", threadId));
        assertEquals("RAN", engine.dispatchCoordinatorPlanningKickoff(syn, "arrietty"));
        assertNull(sent.get());

        String sessionKey = "bot:arrietty:conv:" + threadId;
        ConfigurableWorkflowState state = stateStore.get(sessionKey, ConfigurableWorkflowState.class).orElseThrow();
        assertEquals("true", String.valueOf(state.get(CoordinatorIntakeBootstrapAction.KICKOFF_VISIBLE_OUTCOME_KEY)));
        assertNull(state.get("coordinatorKickoffFailureReason"));
    }

    @Test
    void featureRoomIntakeThreadInteraction_invokesCoordinatorWorkflowOnly() {
        FeatureRoomStateStore frs = new FeatureRoomStateStore();
        List<RoomParticipant> parts = List.of(
                new RoomParticipant(PlanningRole.ORCHESTRATOR, "arrietty", "r1", "A", true));
        frs.put(new FeatureRoomState(
                "c1", "f1", null, "room-ch", "thread-int-eng", null, null, "INTAKE_READY",
                parts, null, Instant.now()));
        router = new Router(new LifecycleContextStore(), frs);
        engine = new VinekeepersEngine(router, stateStore, auditLogs::add);

        AtomicInteger arriettyRuns = new AtomicInteger();
        for (String id : List.of("arrietty")) {
            BotDefinition b = new BotDefinition(
                    id,
                    new Persona(id, ""),
                    new ModelProfile("stub", "stub"),
                    ToolPolicy.allowAll(),
                    new MemoryPolicy(4096));
            engine.registerBot(b);
            engine.registerReasoner(id, new StubReasoner());
        }
        engine.registerRunner("arrietty", (e, s, bid) -> {
            arriettyRuns.incrementAndGet();
            return WorkflowRunResult.continueWithoutReply();
        });
        router.addRouting(new RoutingRule(new RoutingFilter(null, null, null, null, null, null), "arrietty"));

        engine.onEvent(new Event("discord:g:1", "interaction", Map.of(
                "channelId", "thread-int-eng",
                "authorId", "u1",
                "customId", "approve",
                "interactionId", "i1",
                "token", "t1")));

        assertEquals(1, arriettyRuns.get());
    }
}
