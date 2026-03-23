package com.vinekeepers.state.planning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FeatureRoomStateStoreTest {

    private FeatureRoomStateStore store;

    @BeforeEach
    void setUp() {
        store = new FeatureRoomStateStore();
    }

    @Test
    void putAndGetByContextId() {
        FeatureRoomState state = roomState("ctx-1", "ch-1", "thread-1", "feat-1",
                List.of(participant(PlanningRole.ORCHESTRATOR, "arrietty", "inst-1")));
        store.put(state);
        assertEquals(state, store.getByContextId("ctx-1").orElse(null));
        assertTrue(store.getByContextId("unknown").isEmpty());
    }

    @Test
    void getByContextId_returnsEmptyForNullOrBlank() {
        assertTrue(store.getByContextId(null).isEmpty());
        assertTrue(store.getByContextId("").isEmpty());
        assertTrue(store.getByContextId("   ").isEmpty());
    }

    @Test
    void putAndGetByRoomChannelId() {
        FeatureRoomState state = roomState("ctx-1", "room-ch-1", "thread-1", "feat-1",
                List.of(participant(PlanningRole.ORCHESTRATOR, "arrietty", "inst-1")));
        store.put(state);
        assertEquals(state, store.getByRoomChannelId("room-ch-1").orElse(null));
        assertTrue(store.getByRoomChannelId("other").isEmpty());
    }

    @Test
    void getByRoomChannelId_returnsEmptyForNullOrBlank() {
        assertTrue(store.getByRoomChannelId(null).isEmpty());
        assertTrue(store.getByRoomChannelId("").isEmpty());
    }

    @Test
    void putAndGetByIntakeThreadId() {
        FeatureRoomState state = roomState("ctx-1", "ch-1", "intake-thread-99", "feat-1",
                List.of(participant(PlanningRole.ORCHESTRATOR, "arrietty", "inst-1")));
        store.put(state);
        assertEquals(state, store.getByIntakeThreadId("intake-thread-99").orElse(null));
    }

    @Test
    void getByDeliveryTargetId_resolvesByThreadThenChannel() {
        FeatureRoomState state = roomState("ctx-1", "room-ch", "intake-thread", "feat-1",
                List.of(participant(PlanningRole.ORCHESTRATOR, "arrietty", "inst-1")));
        store.put(state);
        assertEquals(state, store.getByDeliveryTargetId("intake-thread").orElse(null));
        assertEquals(state, store.getByDeliveryTargetId("room-ch").orElse(null));
        assertTrue(store.getByDeliveryTargetId("other").isEmpty());
    }

    @Test
    void putAndGetByFeatureId() {
        FeatureRoomState state = roomState("ctx-1", "ch-1", "thread-1", "feature-xyz",
                List.of(participant(PlanningRole.ORCHESTRATOR, "arrietty", "inst-1")));
        store.put(state);
        assertEquals(state, store.getByFeatureId("feature-xyz").orElse(null));
    }

    @Test
    void put_nullState_doesNothing() {
        store.put(null);
        assertTrue(store.getByContextId("any").isEmpty());
    }

    @Test
    void put_skipsBlankOptionalIndexKeys() {
        FeatureRoomState state = new FeatureRoomState(
                "ctx-1", null, null, "ch-1", null, null, null, "INTAKE_READY",
                List.of(participant(PlanningRole.ORCHESTRATOR, "arrietty", "inst-1")),
                null, null);
        store.put(state);
        assertTrue(store.getByFeatureId("").isEmpty());
        assertTrue(store.getByIntakeThreadId("").isEmpty());
    }

    @Test
    void getParticipantBotIds_returnsStableOrderFromParticipantList() {
        List<RoomParticipant> participants = List.of(participant(PlanningRole.ORCHESTRATOR, "arrietty", "inst-o"));
        FeatureRoomState state = new FeatureRoomState(
                "ctx-1", "f1", null, "ch-1", "thread-1", null, null, "INTAKE_READY",
                participants, null, Instant.now());
        store.put(state);
        List<String> ids = store.getParticipantBotIds(state);
        assertEquals(List.of("arrietty"), ids);
    }

    @Test
    void getParticipantBotIds_whenParticipantsInWrongOrder_returnsRoleOrderOrchestratorArchitectAuditorScribe() {
        List<RoomParticipant> wrongOrder = List.of(
                participant(PlanningRole.SCRIBE, "scribe", "inst-s"),
                participant(PlanningRole.ORCHESTRATOR, "arrietty", "inst-o"));
        FeatureRoomState state = new FeatureRoomState(
                "ctx-1", "f1", null, "ch-1", "thread-1", null, null, "INTAKE_READY",
                wrongOrder, null, Instant.now());
        store.put(state);
        List<String> ids = store.getParticipantBotIds(state);
        assertEquals(List.of("arrietty", "scribe"), ids);
    }

    @Test
    void getParticipantBotIds_emptyOrNullState_returnsEmptyList() {
        assertEquals(List.of(), store.getParticipantBotIds(null));
        FeatureRoomState emptyParticipants = new FeatureRoomState(
                "ctx-1", null, null, "ch-1", null, null, null, "INTAKE_READY",
                List.of(), null, null);
        assertEquals(List.of(), store.getParticipantBotIds(emptyParticipants));
    }

    @Test
    void resolveCoordinatorConfiguredBotId_returnsPrimaryCoordinatorWhenSet() {
        List<RoomParticipant> participants = List.of(
                participant(PlanningRole.ORCHESTRATOR, "arrietty", "i-o", true));
        FeatureRoomState state = roomState("ctx-1", "ch-1", "t-1", "f1", participants);
        assertEquals("arrietty", FeatureRoomStateStore.resolveCoordinatorConfiguredBotId(state).orElse(null));
    }

    @Test
    void resolveCoordinatorConfiguredBotId_prefersExplicitPrimaryOverOrchestratorOrder() {
        List<RoomParticipant> participants = List.of(
                participant(PlanningRole.ORCHESTRATOR, "arrietty", "i-o", false),
                participant(PlanningRole.ARCHITECT, "architect", "i-a", true));
        FeatureRoomState state = roomState("ctx-1", "ch-1", "t-1", "f1", participants);
        assertEquals("architect", FeatureRoomStateStore.resolveCoordinatorConfiguredBotId(state).orElse(null));
    }

    @Test
    void resolveCoordinatorConfiguredBotId_whenNoPrimary_fallsBackToOrchestrator() {
        List<RoomParticipant> participants = List.of(
                participant(PlanningRole.ARCHITECT, "architect", "i-a", false),
                participant(PlanningRole.ORCHESTRATOR, "arrietty", "i-o", false));
        FeatureRoomState state = roomState("ctx-1", "ch-1", "t-1", "f1", participants);
        assertEquals("arrietty", FeatureRoomStateStore.resolveCoordinatorConfiguredBotId(state).orElse(null));
    }

    @Test
    void resolveCoordinatorConfiguredBotId_emptyOrNull_returnsEmpty() {
        assertTrue(FeatureRoomStateStore.resolveCoordinatorConfiguredBotId(null).isEmpty());
        FeatureRoomState emptyParticipants = new FeatureRoomState(
                "ctx-1", null, null, "ch-1", null, null, null, "INTAKE_READY",
                List.of(), null, null);
        assertTrue(FeatureRoomStateStore.resolveCoordinatorConfiguredBotId(emptyParticipants).isEmpty());
    }

    private static FeatureRoomState roomState(String contextId, String roomChannelId,
                                              String intakeThreadId, String featureId,
                                              List<RoomParticipant> participants) {
        return new FeatureRoomState(contextId, featureId, null, roomChannelId, intakeThreadId,
                null, null, "INTAKE_READY", participants, null, Instant.now());
    }

    private static RoomParticipant participant(PlanningRole role, String configuredBotId, String runtimeBotInstanceId) {
        return participant(role, configuredBotId, runtimeBotInstanceId, role == PlanningRole.ORCHESTRATOR);
    }

    private static RoomParticipant participant(PlanningRole role, String configuredBotId, String runtimeBotInstanceId,
                                            boolean primaryCoordinator) {
        return new RoomParticipant(role, configuredBotId, runtimeBotInstanceId, configuredBotId, primaryCoordinator);
    }
}
