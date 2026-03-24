package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanningCanonicalNextAction;
import com.vinekeepers.state.planning.PlanningIntakeStage;
import com.vinekeepers.state.planning.PlanningInteractionState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanningRoutingBridgeClampTest {

    @Test
    void clamp_rewritesBenignBlockedSnapshotWhenDecisionSucceededWithAsk() {
        FeaturePlanState plan = PlanningEvaluationServiceTest.minimalCoherentPlan();
        PlanningEvaluationDecision decision =
                new PlanningEvaluationDecision(
                        true,
                        PlanningCanonicalNextAction.ASK_USER,
                        PlanningIntakeStage.CLARIFYING,
                        PlanningInteractionState.WAITING_FOR_TEXT_REPLY,
                        "MATERIALIZED_NOT_INSPECTED",
                        new PlanningEvaluationDecision.EvaluationConfidence(50, "medium", "ok"),
                        List.of(
                                CanonicalPlanningGap.fromEvaluation(
                                        "g1",
                                        "BRANCHING_DECISION",
                                        "Pick a direction.",
                                        false,
                                        true,
                                        true,
                                        "",
                                        List.of())),
                        true,
                        new PlanningEvaluationDecision.EvaluationBestQuestion("Workflow or runtime?", ""),
                        "g1",
                        "Pick a direction.",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        false,
                        false,
                        false,
                        false,
                        "",
                        "",
                        false,
                        false,
                        "");
        PlanningDecisionSnapshot bad =
                new PlanningDecisionSnapshot(PlanningNextAction.BLOCKED, 50, "", "stale", "", "");
        PlanningDecisionSnapshot fixed = PlanningRoutingBridge.clampBenignBlockedSnapshot(bad, decision, plan);
        assertEquals(PlanningNextAction.ASK_USER, fixed.nextAction());
        assertEquals("Workflow or runtime?", fixed.nextQuestion());
    }
}
