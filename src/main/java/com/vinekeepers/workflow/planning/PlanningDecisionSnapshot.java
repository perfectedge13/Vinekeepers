package com.vinekeepers.workflow.planning;

/**
 * The exclusive live routing snapshot written after planning evaluation.
 */
public record PlanningDecisionSnapshot(
        PlanningNextAction nextAction,
        Integer lastConfidence,
        String nextQuestion,
        String blockingReason,
        String repoEvidenceStatus,
        String decisionSummary) {

    public PlanningDecisionSnapshot {
        nextAction = nextAction != null ? nextAction : PlanningNextAction.BLOCKED;
        nextQuestion = blankToEmpty(nextQuestion);
        blockingReason = blankToEmpty(blockingReason);
        repoEvidenceStatus = blankToEmpty(repoEvidenceStatus);
        decisionSummary = blankToEmpty(decisionSummary);
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
