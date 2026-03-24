package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanningCanonicalNextAction;
import com.vinekeepers.state.planning.PlanningIntakeStage;
import com.vinekeepers.state.planning.PlanningInteractionState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanningDecisionNormalizerTest {

    @Test
    void returnsAskUserOnlyWhenEvaluationIncludesQuestion() {
        PlanningDecisionSnapshot snapshot =
                PlanningDecisionNormalizer.fromEvaluation(
                        decision(
                                true,
                                PlanningCanonicalNextAction.ASK_USER,
                                true,
                                false,
                                "Which repo module owns this rollout?",
                                "",
                                "MATERIALIZED_OBSERVED"));

        assertEquals(PlanningNextAction.ASK_USER, snapshot.nextAction());
        assertEquals("Which repo module owns this rollout?", snapshot.nextQuestion());
    }

    @Test
    void blocksWhenAskUserHasBlankQuestionWithoutCoherentPlanSafetyNet() {
        PlanningDecisionSnapshot snapshot =
                PlanningDecisionNormalizer.fromEvaluation(
                        decision(
                                true,
                                PlanningCanonicalNextAction.ASK_USER,
                                true,
                                false,
                                "",
                                "Need one concrete authority answer.",
                                "MATERIALIZED_OBSERVED"),
                        null);

        assertEquals(PlanningNextAction.BLOCKED, snapshot.nextAction());
        assertEquals("Need one concrete authority answer.", snapshot.blockingReason());
    }

    @Test
    void askRequiredWithBlankQuestionPacketizesWhenCoherentDraftPresent() {
        FeaturePlanState plan = PlanningEvaluationServiceTest.minimalCoherentPlan();
        PlanningDecisionSnapshot snapshot =
                PlanningDecisionNormalizer.fromEvaluation(
                        decision(
                                true,
                                PlanningCanonicalNextAction.ASK_USER,
                                true,
                                false,
                                "",
                                "",
                                "MATERIALIZED_OBSERVED"),
                        plan);

        assertEquals(PlanningNextAction.READY_FOR_PACKET, snapshot.nextAction());
    }

    @Test
    void keepsReadyForPacketWhenEvaluationClearsIt() {
        PlanningDecisionSnapshot snapshot =
                PlanningDecisionNormalizer.fromEvaluation(
                        decision(
                                true,
                                PlanningCanonicalNextAction.READY_FOR_PACKET,
                                false,
                                true,
                                "",
                                "",
                                "MATERIALIZED_OBSERVED"));

        assertEquals(PlanningNextAction.READY_FOR_PACKET, snapshot.nextAction());
    }

    @Test
    void mapsContinueSynthesisToReadyForPacket() {
        PlanningDecisionSnapshot snapshot =
                PlanningDecisionNormalizer.fromEvaluation(
                        decision(
                                true,
                                PlanningCanonicalNextAction.CONTINUE_SYNTHESIS,
                                false,
                                false,
                                "",
                                "",
                                "MATERIALIZED_NOT_INSPECTED"));

        assertEquals(PlanningNextAction.READY_FOR_PACKET, snapshot.nextAction());
    }

    private static PlanningEvaluationDecision decision(
            boolean success,
            PlanningCanonicalNextAction nextAction,
            boolean askUserRequired,
            boolean readyForPacket,
            String question,
            String blockReason,
            String repoEvidenceStatus) {
        return new PlanningEvaluationDecision(
                success,
                nextAction,
                askUserRequired ? PlanningIntakeStage.CLARIFYING : PlanningIntakeStage.DRAFTING,
                askUserRequired ? PlanningInteractionState.WAITING_FOR_TEXT_REPLY : PlanningInteractionState.NONE,
                repoEvidenceStatus,
                new PlanningEvaluationDecision.EvaluationConfidence(62, "medium", "snapshot test"),
                List.of(),
                askUserRequired,
                new PlanningEvaluationDecision.EvaluationBestQuestion(question, ""),
                "",
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                readyForPacket,
                readyForPacket,
                false,
                false,
                blockReason,
                "snapshot test",
                false,
                false,
                "");
    }
}
