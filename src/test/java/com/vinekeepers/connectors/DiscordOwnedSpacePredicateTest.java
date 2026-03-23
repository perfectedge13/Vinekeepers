package com.vinekeepers.connectors;

import com.vinekeepers.state.LifecycleContext;
import com.vinekeepers.state.LifecycleContextStore;
import com.vinekeepers.state.planning.FeatureRoomState;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.planning.PlanningRole;
import com.vinekeepers.state.planning.RoomParticipant;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordOwnedSpacePredicateTest {

    @Test
    void featureRoomIntakeThread_matchesParticipant() {
        FeatureRoomStateStore rooms = new FeatureRoomStateStore();
        String threadId = "thread-99";
        List<RoomParticipant> participants = List.of(
                new RoomParticipant(PlanningRole.ORCHESTRATOR, "arrietty", "r1", "A", true));
        FeatureRoomState state = new FeatureRoomState(
                "ctx-1", "feat-1", "slug", "room-1", threadId, "r/r", "req", "INTAKE_READY",
                participants, "u1", Instant.now());
        rooms.put(state);

        DiscordOwnedSpacePredicate p = new DiscordOwnedSpacePredicate(rooms, new LifecycleContextStore(), "arrietty");
        assertTrue(p.allowsChannel(threadId));
        assertTrue(p.allowsChannel("room-1"));
        assertFalse(p.allowsChannel("other-channel"));
    }

    @Test
    void nonParticipantBot_isNotAllowedInFeatureRoom() {
        FeatureRoomStateStore rooms = new FeatureRoomStateStore();
        List<RoomParticipant> participants = List.of(
                new RoomParticipant(PlanningRole.ORCHESTRATOR, "arrietty", "r1", "A", true));
        rooms.put(new FeatureRoomState(
                "ctx", "f", "s", "room", "th", "r", "x", "S", participants, "u", Instant.now()));

        assertTrue(new DiscordOwnedSpacePredicate(rooms, null, "arrietty").allowsChannel("th"));
        assertFalse(new DiscordOwnedSpacePredicate(rooms, null, "stranger").allowsChannel("th"));
    }

    @Test
    void legacyLifecycle_ownerMatches() {
        LifecycleContextStore lc = new LifecycleContextStore();
        lc.put(new LifecycleContext("lc1", "chan-legacy", Instant.now(), null,
                "arrietty", "run1", null, null, null, null));

        DiscordOwnedSpacePredicate p = new DiscordOwnedSpacePredicate(null, lc, "arrietty");
        assertTrue(p.allowsChannel("chan-legacy"));
        assertFalse(p.allowsChannel("other"));
    }
}
