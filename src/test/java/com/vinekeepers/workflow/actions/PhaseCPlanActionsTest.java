package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileLoader;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.planning.PlanApprovalStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhaseCPlanActionsTest {

    @Test
    void runPlanCritiqueSpreadsReadiness() {
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new FeatureRoomStateStore(), reg);
        assertEquals("OK", init.run(null, Map.of("contextId", "c1", "channelId", "room1"), Map.of()));
        @SuppressWarnings("unchecked")
        Map<String, Object> spread = (Map<String, Object>) new RunPlanCritiqueAndReadinessAction(store, reg)
                .run(null, Map.of("contextId", "c1"), Map.of());
        assertTrue(spread.containsKey("planReadinessStatus"));
        assertEquals("", spread.get("planCritiqueError"));
        FeaturePlanState p = store.getByContextId("c1").orElseThrow();
        assertTrue(p.getPlanCritiqueSnapshot() != null);
        assertTrue(p.getPlanConfidence() != null);
    }

    @Test
    void persistPlanApprovalWritesState() {
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        var init = new InitializeFeaturePlanStateAction(store, new FeatureRoomStateStore(), reg);
        init.run(null, Map.of("contextId", "c2", "channelId", "r2"), Map.of());
        var action = new PersistPlanApprovalAction(store);
        Object r = action.run(
                new Event("discord", "message", Map.of()),
                Map.of("contextId", "c2", "planApprovalDecision", "approve"),
                Map.of("__event", Map.of("authorId", "u9")));
        assertEquals("OK", r);
        assertEquals(PlanApprovalStatus.APPROVE, store.getByContextId("c2").orElseThrow().getPlanApproval().getStatus());
        assertEquals("u9", store.getByContextId("c2").orElseThrow().getPlanApproval().getActorId());
    }
}
