package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.LifecycleContextStore;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomState;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.planning.PlanningRole;
import com.vinekeepers.state.planning.RoomParticipant;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostPlanningPacketThreadActionTest {

    @Test
    void secondPostWithSameBodySkipsDiscordSendAndSetsFlag() {
        List<String> sent = new ArrayList<>();
        FeatureRoomStateStore roomStore = new FeatureRoomStateStore();
        roomStore.put(
                new FeatureRoomState(
                        "ctx1",
                        "f1",
                        "slug",
                        "thread-target",
                        "thread-target",
                        "org/r",
                        "Do the thing",
                        "ACTIVE",
                        List.of(new RoomParticipant(PlanningRole.ORCHESTRATOR, "orch", "ri", "O", true)),
                        "u",
                        Instant.now()));

        OutboundDeliveryRouter router = new OutboundDeliveryRouter(new LifecycleContextStore(), roomStore);
        router.registerSender("orch", (ch, m, body) -> sent.add(body), null);

        FeaturePlanStateStore planStore = new FeaturePlanStateStore();
        planStore.put(minimalPlan("ctx1"));

        PostPlanningPacketThreadAction action = new PostPlanningPacketThreadAction(router, planStore);
        Event ev = new Event("t", "k", Map.of());

        Map<String, Object> state = baseWorkflowState();
        @SuppressWarnings("unchecked")
        Map<String, Object> spread1 = (Map<String, Object>) action.run(ev, state, Map.of());
        assertEquals("true", spread1.get("planningPacketPosted"));
        assertEquals("false", spread1.get("planningPacketSkippedDuplicate"));
        int chunks = Integer.parseInt(spread1.get("planningPacketChunkCount").toString());
        assertTrue(chunks > 0);
        assertEquals(chunks + 1, sent.size());
        assertTrue(sent.get(0).contains("posting the planning packet now"));

        Map<String, Object> state2 = new LinkedHashMap<>(state);
        state2.putAll(spread1);
        @SuppressWarnings("unchecked")
        Map<String, Object> spread2 = (Map<String, Object>) action.run(ev, state2, Map.of());
        assertEquals("false", spread2.get("planningPacketPosted"));
        assertEquals("true", spread2.get("planningPacketSkippedDuplicate"));
        assertEquals(spread1.get("planningPacketPostedVersion"), spread2.get("planningPacketPostedVersion"));
        assertEquals(chunks + 1, sent.size());
        String skipReason = String.valueOf(spread2.get("planningPacketSkipReason"));
        assertTrue(
                skipReason.contains("unchanged"),
                "expected user-visible skip reason, got: " + skipReason);
    }

    @Test
    void blocksWhenPacketPostingNotAllowed() {
        List<String> sent = new ArrayList<>();
        FeatureRoomStateStore roomStore = new FeatureRoomStateStore();
        roomStore.put(
                new FeatureRoomState(
                        "ctx3",
                        "f3",
                        "slug",
                        "th3",
                        "th3",
                        "org/r",
                        "Req",
                        "ACTIVE",
                        List.of(new RoomParticipant(PlanningRole.ORCHESTRATOR, "orch3", "ri", "O", true)),
                        "u",
                        Instant.now()));
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(new LifecycleContextStore(), roomStore);
        router.registerSender("orch3", (ch, m, body) -> sent.add(body), null);
        FeaturePlanStateStore planStore = new FeaturePlanStateStore();
        planStore.put(minimalPlan("ctx3"));
        PostPlanningPacketThreadAction action = new PostPlanningPacketThreadAction(router, planStore);
        Event ev = new Event("t", "k", Map.of());
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("channelId", "th3");
        state.put("contextId", "ctx3");
        state.put("planningPacketDepthOk", "true");
        state.put("planningCanonicalNextAction", "ASK_ONE_QUESTION");
        @SuppressWarnings("unchecked")
        Map<String, Object> spread = (Map<String, Object>) action.run(ev, state, Map.of());
        assertEquals("false", spread.get("planningPacketPosted"));
        assertTrue(String.valueOf(spread.get("planningPacketPostError")).contains("does not allow packet posting"));
        assertEquals(0, sent.size());
    }

    @Test
    void forceRepostSendsAgain() {
        List<String> sent = new ArrayList<>();
        FeatureRoomStateStore roomStore = new FeatureRoomStateStore();
        roomStore.put(
                new FeatureRoomState(
                        "ctx2",
                        "f2",
                        "slug",
                        "th2",
                        "th2",
                        "org/r",
                        "Req",
                        "ACTIVE",
                        List.of(new RoomParticipant(PlanningRole.ORCHESTRATOR, "orch2", "ri", "O", true)),
                        "u",
                        Instant.now()));
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(new LifecycleContextStore(), roomStore);
        router.registerSender("orch2", (ch, m, body) -> sent.add(body), null);
        FeaturePlanStateStore planStore = new FeaturePlanStateStore();
        planStore.put(minimalPlan("ctx2"));
        PostPlanningPacketThreadAction action = new PostPlanningPacketThreadAction(router, planStore);
        Event ev = new Event("t", "k", Map.of());
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("channelId", "th2");
        state.put("contextId", "ctx2");
        state.put("planningPacketDepthOk", "true");
        state.put("planningPacketPostingAllowed", "true");
        state.put("planningCanonicalNextAction", "POST_PACKET");

        @SuppressWarnings("unchecked")
        Map<String, Object> spread1 = (Map<String, Object>) action.run(ev, state, Map.of());
        int chunks = Integer.parseInt(spread1.get("planningPacketChunkCount").toString());
        Map<String, Object> state2 = new LinkedHashMap<>(state);
        state2.putAll(spread1);
        action.run(ev, state2, Map.of("forceRepost", "true"));
        assertEquals((chunks + 1) * 2, sent.size());
    }

    private static Map<String, Object> baseWorkflowState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("channelId", "thread-target");
        state.put("contextId", "ctx1");
        state.put("planningPacketDepthOk", "true");
        state.put("planningPacketPostingAllowed", "true");
        state.put("planningCanonicalNextAction", "POST_PACKET");
        return state;
    }

    private static FeaturePlanState minimalPlan(String contextId) {
        return new FeaturePlanState(
                contextId,
                "f",
                "s",
                "room",
                null,
                null,
                "t",
                "Hello planning packet body seed",
                "PLANNING",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                FeaturePlanState.initialSectionStatuses(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "software_feature_planning",
                Map.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
