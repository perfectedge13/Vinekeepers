package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.PlanConfidence;
import com.vinekeepers.state.planning.PlanCritiqueLifecycleStatus;
import com.vinekeepers.state.planning.PlanCritiqueRubricScores;
import com.vinekeepers.state.planning.PlanCritiqueSnapshot;
import com.vinekeepers.state.planning.PlanReadinessStatus;
import com.vinekeepers.state.planning.PlanningIntakeStage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluatePlanningApprovalGateActionTest {

    @Test
    void gateOpenWhenAllSignalsTrue() {
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        Instant t = Instant.now();
        FeaturePlanState p = approvalReadyPlan("c1", t);
        store.put(p);

        Map<String, Object> state = new HashMap<>();
        state.put("contextId", "c1");
        state.put("planningPacketPostedVersion", "1");
        state.put("planningPacketDepthOk", "true");
        state.put("planReadinessStatus", "READY");
        state.put("planningUserInputRequired", "false");
        @SuppressWarnings("unchecked")
        Map<String, Object> spread =
                (Map<String, Object>) new EvaluatePlanningApprovalGateAction(store)
                        .run(new Event("x", "m", Map.of()), state, Map.of());
        assertEquals("true", spread.get("planningReadyForApproval"));
        assertEquals("true", spread.get("approvalReady"));
        assertEquals("true", spread.get("planningReviewReady"));
    }

    @Test
    void gateClosedWhenDepthFails() {
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        Instant t = Instant.now();
        store.put(approvalReadyPlan("c2", t));

        Map<String, Object> state = new HashMap<>();
        state.put("contextId", "c2");
        state.put("planningPacketPostedVersion", "1");
        state.put("planningPacketDepthOk", "false");
        state.put("planReadinessStatus", "READY");
        state.put("planningUserInputRequired", "false");
        @SuppressWarnings("unchecked")
        Map<String, Object> spread =
                (Map<String, Object>) new EvaluatePlanningApprovalGateAction(store)
                        .run(new Event("x", "m", Map.of()), state, Map.of());
        assertEquals("false", spread.get("planningReadyForApproval"));
        assertEquals("false", spread.get("planningReviewReady"));
    }

    @Test
    void gateClosedWhenClarificationPending() {
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        Instant t = Instant.now();
        store.put(approvalReadyPlan("c3", t));

        Map<String, Object> state = new HashMap<>();
        state.put("contextId", "c3");
        state.put("planningPacketPostedVersion", "1");
        state.put("planningPacketDepthOk", "true");
        state.put("planningUserInputRequired", "true");
        @SuppressWarnings("unchecked")
        Map<String, Object> spread =
                (Map<String, Object>) new EvaluatePlanningApprovalGateAction(store)
                        .run(new Event("x", "m", Map.of()), state, Map.of());
        assertEquals("false", spread.get("planningReadyForApproval"));
        assertEquals("false", spread.get("planningReviewReady"));
        assertTrue(String.valueOf(spread.get("planningApprovalGateReason")).contains("clarification"));
    }

    @Test
    void gateOpensForConditionalReadinessAfterHumanAck() {
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        Instant t = Instant.now();
        FeaturePlanState plan = approvalReadyPlan("c4", t)
                .withPlanConfidence(new PlanConfidence(
                        "HIGH",
                        "conditional",
                        PlanReadinessStatus.CONDITIONALLY_READY,
                        t,
                        0.9,
                        List.of()));
        store.put(plan);

        Map<String, Object> state = new HashMap<>();
        state.put("contextId", "c4");
        state.put("planningPacketPostedVersion", "1");
        state.put("planningPacketDepthOk", "true");
        state.put("planningHumanReadinessAcknowledged", "true");
        state.put("planningUserInputRequired", "false");
        @SuppressWarnings("unchecked")
        Map<String, Object> spread =
                (Map<String, Object>) new EvaluatePlanningApprovalGateAction(store)
                        .run(new Event("x", "m", Map.of()), state, Map.of());
        assertEquals("true", spread.get("planningReadyForApproval"));
        assertEquals("true", spread.get("approvalReady"));
    }

    private static FeaturePlanState approvalReadyPlan(String contextId, Instant t) {
        PlanCritiqueRubricScores rubric =
                new PlanCritiqueRubricScores(0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9);
        PlanCritiqueSnapshot snap =
                new PlanCritiqueSnapshot(
                        t,
                        "RULES_V1",
                        List.of(),
                        PlanCritiqueLifecycleStatus.COMPLETE,
                        rubric,
                        0,
                        List.of());
        PlanConfidence conf =
                new PlanConfidence("HIGH", "ok", PlanReadinessStatus.READY, t, 0.9, List.of());
        FeaturePlanState base =
                new FeaturePlanState(
                        contextId,
                        "f",
                        "s",
                        "room",
                        null,
                        null,
                        "t",
                        "1234567890123456789012345678901234567890",
                        "PLANNING",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        FeaturePlanState.initialSectionStatuses(),
                        conf,
                        null,
                        snap,
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
                        t,
                        t);
        return base.withPacketPosted(t, "", "fp", 1)
                .withPlanningIntakeStage(PlanningIntakeStage.PACKET_POSTED, null)
                .withCritiqueLifecycleStatus(PlanCritiqueLifecycleStatus.COMPLETE);
    }
}
