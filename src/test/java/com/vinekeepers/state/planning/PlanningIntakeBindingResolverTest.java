package com.vinekeepers.state.planning;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningIntakeBindingResolverTest {

    @Test
    void resolve_planStoreOnly_byIntakeThreadId_findsCoordinatorFromPlan() {
        FeaturePlanState plan =
                basePlan("room-1", "thread-int-1")
                        .withPlanningIntakeStage(PlanningIntakeStage.DRAFTING, Instant.now())
                        .withCoordinatorConfiguredBotId("arrietty");
        FeaturePlanStateStore plans = new FeaturePlanStateStore();
        plans.put(plan);

        PlanningIntakeBindingResolver r = new PlanningIntakeBindingResolver(null, plans);
        var binding = r.resolve("thread-int-1", null).orElseThrow();

        assertEquals("ctx-1", binding.planState().getContextId());
        assertTrue(binding.roomState() == null);
        assertEquals("arrietty", binding.coordinatorConfiguredBotId().orElseThrow());
        assertTrue(binding.eventChannelIsPlanIntakeThread());
        assertTrue(binding.activePlanningSession());
        assertTrue(binding.exclusiveCoordinatorThread());
    }

    @Test
    void resolve_planStoreOnly_byRoomChannelId_whenIntakeThreadRecorded_findsPlan() {
        FeaturePlanState plan =
                basePlan("room-main", "thread-int-2")
                        .withPlanningIntakeStage(PlanningIntakeStage.CLARIFYING, Instant.now())
                        .withCoordinatorConfiguredBotId("coord");
        FeaturePlanStateStore plans = new FeaturePlanStateStore();
        plans.put(plan);

        PlanningIntakeBindingResolver r = new PlanningIntakeBindingResolver(null, plans);
        var binding = r.resolve("room-main", null).orElseThrow();

        assertEquals("thread-int-2", binding.planState().getIntakeThreadId());
        assertFalse(binding.eventChannelIsPlanIntakeThread());
        assertTrue(binding.activePlanningSession());
        assertFalse(binding.exclusiveCoordinatorThread());
    }

    @Test
    void resolve_threadUnderParent_matchesPlanIndexedByRoom() {
        FeaturePlanState plan =
                basePlan("parent-room", "thread-under-parent")
                        .withPlanningIntakeStage(PlanningIntakeStage.DRAFTING, Instant.now())
                        .withCoordinatorConfiguredBotId("c-bot");
        FeaturePlanStateStore plans = new FeaturePlanStateStore();
        plans.put(plan);

        PlanningIntakeBindingResolver r = new PlanningIntakeBindingResolver(null, plans);
        var binding = r.resolve("thread-under-parent", "parent-room").orElseThrow();

        assertTrue(binding.eventChannelIsPlanIntakeThread());
        assertEquals("c-bot", binding.coordinatorConfiguredBotId().orElseThrow());
    }

    @Test
    void resolve_emptyChannelId_returnsEmpty() {
        PlanningIntakeBindingResolver r = new PlanningIntakeBindingResolver(null, new FeaturePlanStateStore());
        assertTrue(r.resolve("", null).isEmpty());
    }

    private static FeaturePlanState basePlan(String roomChannelId, String intakeThreadId) {
        return new FeaturePlanState(
                "ctx-1",
                "fid",
                "slug",
                roomChannelId,
                intakeThreadId,
                null,
                "t",
                "req",
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
                "software_feature_planning_v2",
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
