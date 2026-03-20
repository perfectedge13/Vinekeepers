package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.profile.TestWorkProfiles;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomState;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HydratePlanningSessionActionTest {

    @Test
    void nonIntakeChannel_returnsPlanningIntakeThreadFalse() {
        var action = new HydratePlanningSessionAction(new FeatureRoomStateStore(), new FeaturePlanStateStore());
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(
                new Event("discord:x", "message", Map.of("channelId", "random-channel")),
                Map.of(),
                Map.of());
        assertEquals("false", out.get("planningIntakeThread"));
    }

    @Test
    void intakeThread_spreadsContextAndRepoFromPlan() {
        var rooms = new FeatureRoomStateStore();
        var plans = new FeaturePlanStateStore();
        var reg = TestWorkProfiles.loadFromRepoConfig();
        rooms.put(new FeatureRoomState(
                "ctx-1",
                "f1",
                "slug",
                "room-ch",
                "thread-ch",
                "owner/repo",
                "do the thing",
                "INTAKE_READY",
                java.util.List.of(),
                "u1",
                null));
        assertEquals("OK", new InitializeFeaturePlanStateAction(plans, rooms, reg).run(
                null,
                Map.of("contextId", "ctx-1", "channelId", "room-ch", "deliveryChannelId", "thread-ch", "repo", "owner/repo", "initialRequest", "do the thing"),
                Map.of()));

        var action = new HydratePlanningSessionAction(rooms, plans);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(
                new Event("discord:x", "message", Map.of("channelId", "thread-ch")),
                Map.of(),
                Map.of());
        assertEquals("true", out.get("planningIntakeThread"));
        assertEquals("ctx-1", out.get("contextId"));
        assertEquals("room-ch", out.get("channelId"));
        assertEquals("thread-ch", out.get("deliveryChannelId"));
        assertEquals("owner/repo", out.get("project"));
        assertEquals("do the thing", out.get("codeChange"));
    }
}
