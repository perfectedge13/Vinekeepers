package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanningCanonicalNextAction;

/**
 * One-way conversion from evaluation output to the exclusive live routing snapshot.
 */
public final class PlanningDecisionNormalizer {

    private PlanningDecisionNormalizer() {}

    public static PlanningDecisionSnapshot fromEvaluation(PlanningEvaluationDecision decision) {
        return fromEvaluation(decision, null);
    }

    /**
     * @param plan optional; when present, coherent drafts never map to {@link PlanningNextAction#BLOCKED} for a successful
     *     evaluation that only reported {@link PlanningCanonicalNextAction#BLOCK} without a blocking contradiction.
     */
    public static PlanningDecisionSnapshot fromEvaluation(PlanningEvaluationDecision decision, FeaturePlanState plan) {
        if (decision == null) {
            return new PlanningDecisionSnapshot(
                    PlanningNextAction.BLOCKED,
                    null,
                    "",
                    "Planning evaluation did not return a decision.",
                    "",
                    "");
        }
        String question = decision.canonicalQuestionText();
        PlanningNextAction nextAction = mapNextAction(decision, question, plan);
        return new PlanningDecisionSnapshot(
                nextAction,
                decision.confidence() != null ? decision.confidence().score() : null,
                nextAction == PlanningNextAction.ASK_USER ? question : "",
                nextAction == PlanningNextAction.BLOCKED ? blockingReason(decision) : "",
                decision.repoGroundingState(),
                decision.summary());
    }

    private static PlanningNextAction mapNextAction(
            PlanningEvaluationDecision decision, String question, FeaturePlanState plan) {
        if (!decision.success()) {
            return PlanningNextAction.BLOCKED;
        }
        if (decision.askUserRequired()) {
            if (!question.isBlank()) {
                return PlanningNextAction.ASK_USER;
            }
            if (plan != null
                    && PlanningEvaluationService.isCoherentPlanDraft(plan)
                    && !PlanningEvaluationService.hasBlockingContradiction(decision.gaps())) {
                return PlanningNextAction.READY_FOR_PACKET;
            }
            return PlanningNextAction.BLOCKED;
        }
        if (decision.nextAction() == PlanningCanonicalNextAction.BLOCK
                && plan != null
                && PlanningEvaluationService.isCoherentPlanDraft(plan)
                && !PlanningEvaluationService.hasBlockingContradiction(decision.gaps())) {
            return PlanningNextAction.READY_FOR_PACKET;
        }
        return switch (decision.nextAction()) {
            case READY_FOR_PACKET, CONTINUE_SYNTHESIS -> PlanningNextAction.READY_FOR_PACKET;
            case ASK_USER -> !question.isBlank() ? PlanningNextAction.ASK_USER : PlanningNextAction.BLOCKED;
            case BLOCK -> PlanningNextAction.BLOCKED;
        };
    }

    private static String blockingReason(PlanningEvaluationDecision decision) {
        if (decision == null) {
            return "";
        }
        if (!decision.blockReason().isBlank()) {
            return decision.blockReason();
        }
        if (!decision.machineError().isBlank()) {
            return decision.machineError();
        }
        if (!decision.summary().isBlank()) {
            return decision.summary();
        }
        return "Planning evaluation did not authorize packet posting or a clarification question.";
    }
}
