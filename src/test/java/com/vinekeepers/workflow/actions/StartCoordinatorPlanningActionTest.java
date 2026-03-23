package com.vinekeepers.workflow.actions;

import com.vinekeepers.bot.BotDefinition;
import com.vinekeepers.bot.ConversationMode;
import com.vinekeepers.bot.MemoryPolicy;
import com.vinekeepers.bot.ModelProfile;
import com.vinekeepers.bot.Persona;
import com.vinekeepers.bot.Router;
import com.vinekeepers.bot.ToolPolicy;
import com.vinekeepers.connectors.DiscordReplyTargetResolver;
import com.vinekeepers.core.VinekeepersEngine;
import com.vinekeepers.events.Event;
import com.vinekeepers.reasoner.StubReasoner;
import com.vinekeepers.state.planning.FeatureRoomState;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.planning.PlanningRole;
import com.vinekeepers.state.planning.RoomParticipant;
import com.vinekeepers.tools.ToolRegistry;
import com.vinekeepers.tools.ToolRunner;
import com.vinekeepers.workflow.WorkflowActionRegistry;
import com.vinekeepers.workflow.WorkflowRunnerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartCoordinatorPlanningActionTest {

    private Router router;
    private VinekeepersEngine engine;
    private FeatureRoomStateStore rooms;

    @BeforeEach
    void setUp() {
        router = new Router();
        rooms = new FeatureRoomStateStore();
        engine = new VinekeepersEngine(router, new com.vinekeepers.state.StateStore(), e -> { });
        BotDefinition arrietty = new BotDefinition(
                "arrietty",
                new Persona("A", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096),
                "stub",
                null,
                ConversationMode.CONVERSATIONAL,
                "thread",
                null);
        engine.registerBot(arrietty);
        engine.registerReasoner("arrietty", new StubReasoner());
        engine.setReplySender("discord", (c, m, t) -> { });
        engine.registerReplyTargetResolver("discord", new DiscordReplyTargetResolver());
    }

    @Test
    void missingIntakeThreadId_returnsError() {
        var action = new StartCoordinatorPlanningAction(engine, rooms, null);
        String out = (String) action.run(new Event("discord:g", "message", Map.of()), Map.of(), Map.of());
        assertTrue(out.contains("missing intakeThreadId"));
    }

    @Test
    void threadCreateFailed_returnsError() {
        var action = new StartCoordinatorPlanningAction(engine, rooms, null);
        String out = (String) action.run(new Event("discord:g", "message", Map.of()),
                Map.of(),
                Map.of("intakeThreadId", CreateThreadAction.THREAD_CREATE_FAILED));
        assertTrue(out.contains("not created"));
    }

    @Test
    void missingCoordinator_returnsErrorWhenNoFeatureRoom() {
        var action = new StartCoordinatorPlanningAction(engine, rooms, null);
        String out = (String) action.run(
                new Event("discord:g", "message", Map.of()),
                Map.of(),
                Map.of("intakeThreadId", "unknown-thread-999"));
        assertTrue(out.contains("missing coordinatorBotId"));
    }

    @Test
    void dispatchesWithThreadPayloadAndResolvesCoordinatorFromRoom() {
        String threadId = "111222333444555666";
        rooms.put(new FeatureRoomState(
                "ctx-x",
                "fid",
                "slug",
                "room-1",
                threadId,
                "o/r",
                "change",
                "INTAKE_READY",
                List.of(new RoomParticipant(PlanningRole.ORCHESTRATOR, "arrietty", "inst-1", "A", true)),
                "u1",
                Instant.now()));

        AtomicReference<String> capturedChannel = new AtomicReference<>();
        WorkflowActionRegistry reg = new WorkflowActionRegistry();
        reg.register("record_discord_channel", (e, s, b) -> {
            capturedChannel.set(e.getPayload("channelId", String.class));
            return "ok";
        });
        Map<String, Object> wf = Map.of("steps", List.of(
                Map.of("type", "call_action", "action", "record_discord_channel", "bind", Map.of()),
                Map.of("type", "prompt_for_field", "prompt", "hi", "storeIn", "f"),
                Map.of("type", "done", "message", "ok")));
        BotDefinition arriettyConfigured = new BotDefinition(
                "arrietty",
                new Persona("A", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096),
                "configured",
                Map.of("workflowRef", "thr_kick"),
                ConversationMode.CONVERSATIONAL,
                "thread",
                null);
        engine.registerBot(arriettyConfigured);
        engine.registerRunner("arrietty", WorkflowRunnerFactory.create(arriettyConfigured,
                Map.of("thr_kick", wf), reg, new ToolRunner(new ToolRegistry())));

        var action = new StartCoordinatorPlanningAction(engine, rooms, null);
        Event parent = new Event("discord:g:1", "message",
                Map.of("channelId", "main", "authorId", "user-9", "content", "@Luna launch"));
        assertEquals("RAN", action.run(parent, Map.of("deliveryChannelId", threadId),
                Map.of("intakeThreadId", threadId)));
        assertEquals(threadId, capturedChannel.get());
    }

    @Test
    void duplicateDispatchSkipped() {
        String threadId = "999888777666555444";
        rooms.put(new FeatureRoomState(
                "ctx-y",
                "fid",
                "slug",
                "room-2",
                threadId,
                "o/r",
                "x",
                "INTAKE_READY",
                List.of(new RoomParticipant(PlanningRole.ORCHESTRATOR, "arrietty", "inst-2", "A", true)),
                "u1",
                Instant.now()));

        WorkflowActionRegistry reg = new WorkflowActionRegistry();
        Map<String, Object> wf = Map.of("steps", List.of(
                Map.of("type", "prompt_for_field", "prompt", "w", "storeIn", "field"),
                Map.of("type", "capture_field", "storeIn", "field"),
                Map.of("type", "done", "message", "ok")));
        BotDefinition arriettyConfigured = new BotDefinition(
                "arrietty",
                new Persona("A", ""),
                new ModelProfile("stub", "stub"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(4096),
                "configured",
                Map.of("workflowRef", "dup_kick"),
                ConversationMode.CONVERSATIONAL,
                "thread",
                null);
        engine.registerBot(arriettyConfigured);
        engine.registerRunner("arrietty", WorkflowRunnerFactory.create(arriettyConfigured,
                Map.of("dup_kick", wf), reg, new ToolRunner(new ToolRegistry())));

        var action = new StartCoordinatorPlanningAction(engine, rooms, null);
        Event parent = new Event("discord:g:1", "message", Map.of("authorId", "u1"));
        Map<String, Object> bind = Map.of("intakeThreadId", threadId);
        assertEquals("RAN", action.run(parent, Map.of(), bind));
        assertEquals("SKIPPED_WAITING", action.run(parent, Map.of(), bind));
    }
}
