package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.connectors.ReplySender;
import com.vinekeepers.state.LifecycleContextStore;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostChannelMessageActionTest {

    @Test
    void runReturnsErrorWhenReplySenderNull() {
        PostChannelMessageAction action = new PostChannelMessageAction(null);
        Object result = action.run(null, Map.of(), Map.of());
        assertEquals("Reply sender not available.", result);
    }

    @Test
    void runReturnsErrorWhenChannelIdMissing() {
        PostChannelMessageAction action = new PostChannelMessageAction((ch, msg, content) -> {});
        Object result = action.run(null, Map.of(), Map.of());
        assertEquals("Missing channelId for post_channel_message.", result);
    }

    @Test
    void runSendsContentFromBindThenState() {
        StringBuilder sent = new StringBuilder();
        ReplySender sender = (channelId, messageId, content) ->
                sent.append(channelId).append("|").append(content);
        PostChannelMessageAction action = new PostChannelMessageAction(sender);
        Object result = action.run(null, Map.of("channelId", "ch-1", "content", "Hello"),
                Map.of("channelId", "ch-1", "content", "Hello"));
        assertEquals("OK", result);
        assertEquals("ch-1|Hello", sent.toString());
    }

    @Test
    void runUsesStateWhenBindMissingChannelIdOrContent() {
        StringBuilder sent = new StringBuilder();
        ReplySender sender = (channelId, messageId, content) ->
                sent.append(channelId).append("|").append(content);
        PostChannelMessageAction action = new PostChannelMessageAction(sender);
        action.run(null, Map.of("channelId", "ch-state", "content", "From state"), Map.of());
        assertEquals("ch-state|From state", sent.toString());
    }

    @Test
    void runReturnsErrorWhenContentBlankAndDoesNotSend() {
        StringBuilder sent = new StringBuilder();
        ReplySender sender = (channelId, messageId, content) -> sent.append("sent");
        PostChannelMessageAction action = new PostChannelMessageAction(sender);
        Object result = action.run(null, Map.of("channelId", "ch-1"), Map.of("channelId", "ch-1"));
        assertEquals("Blank content for post_channel_message.", result);
        assertEquals("", sent.toString());
    }

    @Test
    void runInterpolatesContentFromBindState() {
        StringBuilder sent = new StringBuilder();
        ReplySender sender = (channelId, messageId, content) ->
                sent.append(channelId).append("|").append(content);
        PostChannelMessageAction action = new PostChannelMessageAction(sender);
        Map<String, Object> args = Map.of("channelId", "ch-lifecycle", "content",
                "Repo: {{project}}. Request: {{codeChange}}. Launching…", "project", "owner/repo", "codeChange", "Add feature X");
        action.run(null, Map.of("channelId", "ch-lifecycle"), args);
        assertEquals("ch-lifecycle|Repo: owner/repo. Request: Add feature X. Launching…", sent.toString());
    }

    @Test
    void runInterpolatesLifecycleBotNameFromMergedMapBindWins() {
        StringBuilder sent = new StringBuilder();
        ReplySender sender = (channelId, messageId, content) ->
                sent.append(channelId).append("|").append(content);
        PostChannelMessageAction action = new PostChannelMessageAction(sender);
        Map<String, Object> state = Map.of("channelId", "ch-1", "project", "acme/repo", "codeChange", "Phase 1", "lifecycleBotName", "StateBot");
        Map<String, Object> bind = Map.of("content", "Room handled by {{lifecycleBotName}}. Repo: {{project}}.", "lifecycleBotName", "Arrietty");
        action.run(null, state, bind);
        assertEquals("ch-1|Room handled by Arrietty. Repo: acme/repo.", sent.toString());
    }

    @Test
    void runFallsBackToChannelIdWhenDeliveryChannelIdIsThreadCreateFailed() {
        StringBuilder sent = new StringBuilder();
        ReplySender sender = (channelId, messageId, content) ->
                sent.append(channelId).append("|").append(content);
        PostChannelMessageAction action = new PostChannelMessageAction(sender);
        Map<String, Object> state = Map.of("channelId", "ch-room", "deliveryChannelId", CreateThreadAction.THREAD_CREATE_FAILED, "content", "Fallback to channel");
        action.run(null, state, Map.of());
        assertEquals("ch-room|Fallback to channel", sent.toString());
    }

    @Test
    void runWithAsBotId_usesOutboundDeliveryRouterSendAs() {
        LifecycleContextStore lifecycleStore = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(lifecycleStore);
        StringBuilder sent = new StringBuilder();
        ReplySender architectSender = (channelId, messageId, content) ->
                sent.append(channelId).append("|").append(content);
        router.registerSender("architect", architectSender, null);
        router.setDefaultSender((ch, msg, content) -> sent.append("default"));
        PostChannelMessageAction action = new PostChannelMessageAction(router);

        Object result = action.run(null,
                Map.of("channelId", "ch-1", "content", "From architect", "asBotId", "architect"),
                Map.of());

        assertEquals("OK", result);
        assertEquals("ch-1|From architect", sent.toString());
    }

    @Test
    void runWithAsRole_usesOutboundDeliveryRouterSendAsRole() {
        com.vinekeepers.state.planning.FeatureRoomStateStore featureStore = new com.vinekeepers.state.planning.FeatureRoomStateStore();
        java.util.List<com.vinekeepers.state.planning.RoomParticipant> participants = java.util.List.of(
                new com.vinekeepers.state.planning.RoomParticipant(com.vinekeepers.state.planning.PlanningRole.ORCHESTRATOR, "arrietty", "i-o", "Arrietty", true),
                new com.vinekeepers.state.planning.RoomParticipant(com.vinekeepers.state.planning.PlanningRole.ARCHITECT, "architect", "i-a", "Architect", false),
                new com.vinekeepers.state.planning.RoomParticipant(com.vinekeepers.state.planning.PlanningRole.AUDITOR, "auditor", "i-u", "Auditor", false),
                new com.vinekeepers.state.planning.RoomParticipant(com.vinekeepers.state.planning.PlanningRole.SCRIBE, "scribe", "i-s", "Scribe", false));
        com.vinekeepers.state.planning.FeatureRoomState roomState = new com.vinekeepers.state.planning.FeatureRoomState(
                "ctx-1", null, null, "room-ch-1", null, null, null, "INTAKE_READY",
                participants, null, null);
        featureStore.put(roomState);
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(new LifecycleContextStore(), featureStore);
        StringBuilder sent = new StringBuilder();
        router.registerSender("scribe", (channelId, messageId, content) ->
                sent.append(channelId).append("|").append(content), null);
        router.setDefaultSender((ch, msg, content) -> sent.append("default"));
        PostChannelMessageAction action = new PostChannelMessageAction(router);

        Object result = action.run(null,
                Map.of("channelId", "room-ch-1", "content", "Scribe summary", "asRole", "SCRIBE"),
                Map.of());

        assertEquals("OK", result);
        assertEquals("room-ch-1|Scribe summary", sent.toString());
    }

    @Test
    void runWithAsRoleFromBind_overridesState() {
        com.vinekeepers.state.planning.FeatureRoomStateStore featureStore = new com.vinekeepers.state.planning.FeatureRoomStateStore();
        java.util.List<com.vinekeepers.state.planning.RoomParticipant> participants = java.util.List.of(
                new com.vinekeepers.state.planning.RoomParticipant(com.vinekeepers.state.planning.PlanningRole.ORCHESTRATOR, "arrietty", "i-o", "Arrietty", true),
                new com.vinekeepers.state.planning.RoomParticipant(com.vinekeepers.state.planning.PlanningRole.ARCHITECT, "architect", "i-a", "Architect", false),
                new com.vinekeepers.state.planning.RoomParticipant(com.vinekeepers.state.planning.PlanningRole.AUDITOR, "auditor", "i-u", "Auditor", false),
                new com.vinekeepers.state.planning.RoomParticipant(com.vinekeepers.state.planning.PlanningRole.SCRIBE, "scribe", "i-s", "Scribe", false));
        com.vinekeepers.state.planning.FeatureRoomState roomState = new com.vinekeepers.state.planning.FeatureRoomState(
                "ctx-1", null, null, "room-ch-1", null, null, null, "INTAKE_READY",
                participants, null, null);
        featureStore.put(roomState);
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(new LifecycleContextStore(), featureStore);
        StringBuilder sent = new StringBuilder();
        router.registerSender("auditor", (channelId, messageId, content) ->
                sent.append(channelId).append("|").append(content), null);
        router.setDefaultSender((ch, msg, content) -> sent.append("default"));
        PostChannelMessageAction action = new PostChannelMessageAction(router);

        action.run(null,
                Map.of("channelId", "room-ch-1", "content", "Audit", "asRole", "ORCHESTRATOR"),
                Map.of("asRole", "AUDITOR"));

        assertEquals("room-ch-1|Audit", sent.toString());
    }

    @Test
    void runTargetRoom_sendsToChannelId() {
        StringBuilder sent = new StringBuilder();
        ReplySender sender = (channelId, messageId, content) ->
                sent.append(channelId).append("|").append(content);
        PostChannelMessageAction action = new PostChannelMessageAction(sender);
        Map<String, Object> state = Map.of("channelId", "ch-room", "deliveryChannelId", "thread-123", "target", "room", "content", "msg");
        action.run(null, state, Map.of());
        assertEquals("ch-room|msg", sent.toString());
    }

    @Test
    void runTargetThread_sendsToDeliveryChannelId() {
        StringBuilder sent = new StringBuilder();
        ReplySender sender = (channelId, messageId, content) ->
                sent.append(channelId).append("|").append(content);
        PostChannelMessageAction action = new PostChannelMessageAction(sender);
        Map<String, Object> state = Map.of("channelId", "ch-room", "deliveryChannelId", "thread-456", "target", "thread", "content", "msg");
        action.run(null, state, Map.of());
        assertEquals("thread-456|msg", sent.toString());
    }

    @Test
    void runTargetThread_whenDeliveryChannelIdIsThreadCreateFailed_fallsBackToChannelId() {
        StringBuilder sent = new StringBuilder();
        ReplySender sender = (channelId, messageId, content) ->
                sent.append(channelId).append("|").append(content);
        PostChannelMessageAction action = new PostChannelMessageAction(sender);
        Map<String, Object> state = Map.of("channelId", "ch-room", "deliveryChannelId", CreateThreadAction.THREAD_CREATE_FAILED, "target", "thread", "content", "msg");
        action.run(null, state, Map.of());
        assertEquals("ch-room|msg", sent.toString());
    }

    @Test
    void runTargetChannelId_set_usesTargetChannelId() {
        StringBuilder sent = new StringBuilder();
        ReplySender sender = (channelId, messageId, content) ->
                sent.append(channelId).append("|").append(content);
        PostChannelMessageAction action = new PostChannelMessageAction(sender);
        Map<String, Object> state = Map.of("channelId", "ch-room", "deliveryChannelId", "thread-789", "targetChannelId", "explicit-ch", "content", "msg");
        action.run(null, state, Map.of());
        assertEquals("explicit-ch|msg", sent.toString());
    }

    @Test
    void runLegacyFallback_noTargetOrTargetChannelId_usesFirstNonBlankDeliveryChannelIdThenChannelId() {
        StringBuilder sent = new StringBuilder();
        ReplySender sender = (channelId, messageId, content) ->
                sent.append(channelId).append("|").append(content);
        PostChannelMessageAction action = new PostChannelMessageAction(sender);
        Map<String, Object> state = Map.of("channelId", "ch-room", "deliveryChannelId", "thread-legacy", "content", "msg");
        action.run(null, state, Map.of());
        assertEquals("thread-legacy|msg", sent.toString());
    }

    @Test
    void runLegacyFallback_noDeliveryChannelId_usesChannelId() {
        StringBuilder sent = new StringBuilder();
        ReplySender sender = (channelId, messageId, content) ->
                sent.append(channelId).append("|").append(content);
        PostChannelMessageAction action = new PostChannelMessageAction(sender);
        Map<String, Object> state = Map.of("channelId", "ch-only", "content", "msg");
        action.run(null, state, Map.of());
        assertEquals("ch-only|msg", sent.toString());
    }
}
