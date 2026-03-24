package com.vinekeepers.workflow.planning;

/**
 * One-way conversion from evaluation output to the exclusive live routing snapshot.
 */
public final class PlanningDecisionNormalizer {

    private PlanningDecisionNormalizer() {}

    public static PlanningDecisionSnapshot fromEvaluation(PlanningEvaluationDecision decision) {
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
        PlanningNextAction nextAction;
        if (decision.success() && decision.askUserRequired() && !question.isBlank()) {
            nextAction = PlanningNextAction.ASK_USER;
        } else if (decision.success() && decision.readyForPacket()) {
            nextAction = PlanningNextAction.READY_FOR_PACKET;
        } else {
            nextAction = PlanningNextAction.BLOCKED;
        }
        return new PlanningDecisionSnapshot(
                nextAction,
                decision.confidence() != null ? decision.confidence().score() : null,
                nextAction == PlanningNextAction.ASK_USER ? question : "",
                nextAction == PlanningNextAction.BLOCKED ? blockingReason(decision) : "",
                decision.repoGroundingState(),
                decision.summary());
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
