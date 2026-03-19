package com.vinekeepers.workflow.actions;

import com.vinekeepers.profile.TestWorkProfiles;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomState;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.planning.PlanningRole;
import com.vinekeepers.state.planning.RoomParticipant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InitializeFeaturePlanStateActionTest {

    private FeaturePlanStateStore planStore;
    private FeatureRoomStateStore roomStore;
    private com.vinekeepers.profile.WorkProfileRegistry profileRegistry;

    @BeforeEach
    void setUp() {
        planStore = new FeaturePlanStateStore();
        roomStore = new FeatureRoomStateStore();
        profileRegistry = TestWorkProfiles.loadFromRepoConfig();
    }

    @Test
    void runLoadsFromFeatureRoomStateByContextId() {
        FeatureRoomState room = new FeatureRoomState(
                "ctx-1",
                "feat-x",
                "my-slug",
                "room-99",
                "thr-99",
                "org/repo",
                "do the thing",
                "INTAKE_READY",
                List.of(new RoomParticipant(PlanningRole.ORCHESTRATOR, "a", "i1", "A", true)),
                "u1",
                null);
        roomStore.put(room);

        InitializeFeaturePlanStateAction action = new InitializeFeaturePlanStateAction(planStore, roomStore, profileRegistry);
        Object r = action.run(null, Map.of(), Map.of("contextId", "ctx-1"));
        assertEquals("OK", r);
        assertTrue(planStore.getByContextId("ctx-1").isPresent());
        var p = planStore.getByContextId("ctx-1").get();
        assertEquals(InitializeFeaturePlanStateAction.DEFAULT_PROFILE_ID, p.getProfileId());
        assertFalse(p.getArtifacts().isEmpty());
        assertTrue(p.getArtifacts().containsKey("requirements_spec"));
        assertEquals("feat-x", p.getFeatureId());
        assertEquals("my-slug", p.getFeatureSlug());
        assertEquals("room-99", p.getRoomChannelId());
        assertEquals("thr-99", p.getIntakeThreadId());
        assertEquals("org/repo", p.getRepoRef());
        assertEquals("do the thing", p.getInitialRequest());
    }

    @Test
    void runReturnsErrorWhenPlanStoreNull() {
        InitializeFeaturePlanStateAction action = new InitializeFeaturePlanStateAction(null, roomStore, profileRegistry);
        assertEquals("FeaturePlanStateStore not available.", action.run(null, Map.of(), Map.of("contextId", "c")));
    }

    @Test
    void runReturnsErrorWhenContextIdMissing() {
        InitializeFeaturePlanStateAction action = new InitializeFeaturePlanStateAction(planStore, roomStore, profileRegistry);
        assertEquals("Missing contextId for initialize_feature_plan_state.", action.run(null, Map.of(), Map.of()));
    }

    @Test
    void runReturnsErrorWhenNoRoomAndNoChannel() {
        InitializeFeaturePlanStateAction action = new InitializeFeaturePlanStateAction(planStore, roomStore, profileRegistry);
        Object r = action.run(null, Map.of("contextId", "c"), Map.of());
        assertEquals("Missing roomChannelId/channelId for initialize_feature_plan_state.", r);
    }
}
