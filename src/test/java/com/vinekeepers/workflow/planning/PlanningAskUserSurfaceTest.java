package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.PlanningCanonicalNextAction;
import com.vinekeepers.state.planning.PlanningIntakeStage;
import com.vinekeepers.state.planning.PlanningInteractionState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningAskUserSurfaceTest {

    @Test
    void resolveAskGap_fallsBackWhenChosenAskGapNullButSingleEligibleExists() {
        CanonicalPlanningGap branch =
                CanonicalPlanningGap.fromEvaluation(
                        "b1",
                        "BRANCHING_DECISION",
                        "Pick A or B?",
                        false,
                        true,
                        false,
                        "",
                        List.of());
        PlanningEvaluationDecision decision =
                new PlanningEvaluationDecision(
                        true,
                        PlanningCanonicalNextAction.ASK_USER,
                        PlanningIntakeStage.CLARIFYING,
                        PlanningInteractionState.WAITING_FOR_TEXT_REPLY,
                        "MATERIALIZED_NOT_INSPECTED",
                        new PlanningEvaluationDecision.EvaluationConfidence(50, "medium", "x"),
                        List.of(branch),
                        false,
                        new PlanningEvaluationDecision.EvaluationBestQuestion("Pick A or B?", ""),
                        "",
                        "",
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

        CanonicalPlanningGap g = PlanningAskUserSurface.resolveAskGap(decision);
        assertEquals("b1", g.gapId());
    }

    @Test
    void resolveAskGap_usesRecoveryWhenNoEligibleGaps() {
        PlanningEvaluationDecision decision =
                new PlanningEvaluationDecision(
                        true,
                        PlanningCanonicalNextAction.ASK_USER,
                        PlanningIntakeStage.CLARIFYING,
                        PlanningInteractionState.WAITING_FOR_TEXT_REPLY,
                        "MATERIALIZED_NOT_INSPECTED",
                        new PlanningEvaluationDecision.EvaluationConfidence(50, "medium", "x"),
                        List.of(),
                        false,
                        new PlanningEvaluationDecision.EvaluationBestQuestion("What is the scope?", ""),
                        "",
                        "",
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

        CanonicalPlanningGap g = PlanningAskUserSurface.resolveAskGap(decision);
        assertEquals(PlanningAskUserSurface.RECOVERY_GAP_ID, g.gapId());
        assertTrue(g.askable());
    }

    @Test
    void resolveQuestionText_prefersSnapshotNextQuestion() {
        PlanningEvaluationDecision decision =
                new PlanningEvaluationDecision(
                        true,
                        PlanningCanonicalNextAction.ASK_USER,
                        PlanningIntakeStage.CLARIFYING,
                        PlanningInteractionState.WAITING_FOR_TEXT_REPLY,
                        "MATERIALIZED_NOT_INSPECTED",
                        new PlanningEvaluationDecision.EvaluationConfidence(50, "medium", "x"),
                        List.of(),
                        true,
                        new PlanningEvaluationDecision.EvaluationBestQuestion("from decision", ""),
                        "",
                        "",
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
        PlanningDecisionSnapshot snap =
                new PlanningDecisionSnapshot(
                        PlanningNextAction.ASK_USER, 50, "from snapshot", "", "MATERIALIZED_NOT_INSPECTED", "");

        assertEquals("from snapshot", PlanningAskUserSurface.resolveQuestionText(snap, decision));
    }

    @Test
    void resolveQuestionText_fallsBackToDecisionWhenSnapshotEmpty() {
        PlanningEvaluationDecision decision =
                new PlanningEvaluationDecision(
                        true,
                        PlanningCanonicalNextAction.ASK_USER,
                        PlanningIntakeStage.CLARIFYING,
                        PlanningInteractionState.WAITING_FOR_TEXT_REPLY,
                        "MATERIALIZED_NOT_INSPECTED",
                        new PlanningEvaluationDecision.EvaluationConfidence(50, "medium", "x"),
                        List.of(),
                        true,
                        new PlanningEvaluationDecision.EvaluationBestQuestion("from decision", ""),
                        "",
                        "",
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
        PlanningDecisionSnapshot snap =
                new PlanningDecisionSnapshot(PlanningNextAction.ASK_USER, 50, "", "", "MATERIALIZED_NOT_INSPECTED", "");

        assertEquals("from decision", PlanningAskUserSurface.resolveQuestionText(snap, decision));
    }
}
