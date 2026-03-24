package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.CoordinatorClarificationEnginePolicy;
import com.vinekeepers.profile.CoordinatorClarificationSettings;
import com.vinekeepers.state.planning.ClarificationResolutionDecision;

/**
 * Sole production authority for ask / assume / defer / block on a single coordinator gap, given engine policy and
 * evidence scores. Gap enumeration is internal to {@link CanonicalPlanningGapEngine}.
 */
public final class CanonicalGapResolutionPolicy {

    private CanonicalGapResolutionPolicy() {}

    public static CanonicalPlanningGapEngine.AssessedGap resolveOneGap(
            CanonicalPlanningGap gap,
            CoordinatorClarificationSettings settings,
            double evidenceScore,
            double askThreshold,
            CoordinatorClarificationEnginePolicy policy,
            boolean budgetExhausted,
            int gapAskCount) {
        boolean gapBudgetExhausted =
                policy.getMaxClarificationTurnsPerGap() > 0 && gapAskCount >= policy.getMaxClarificationTurnsPerGap();
        if (gap.blocking()) {
            if (budgetExhausted || gapBudgetExhausted) {
                return new CanonicalPlanningGapEngine.AssessedGap(
                        gap,
                        ClarificationResolutionDecision.BLOCK_AS_UNIMPLEMENTABLE,
                        "Coordinator clarification budget exhausted while a blocking planning gap remains open ("
                                + gap.gapId()
                                + ").");
            }
            return new CanonicalPlanningGapEngine.AssessedGap(gap, ClarificationResolutionDecision.ASK_USER, "");
        }
        if (budgetExhausted || gapBudgetExhausted) {
            if (policy.isAllowAssumeAndContinue()) {
                return new CanonicalPlanningGapEngine.AssessedGap(
                        gap,
                        ClarificationResolutionDecision.ASSUME_AND_CONTINUE,
                        defaultAssumptionLine(gap));
            }
            return new CanonicalPlanningGapEngine.AssessedGap(gap, ClarificationResolutionDecision.LOW_PRIORITY_DEFER, "");
        }
        if (policy.isAllowAssumeAndContinue() && evidenceScore >= askThreshold) {
            return new CanonicalPlanningGapEngine.AssessedGap(
                    gap,
                    ClarificationResolutionDecision.ASSUME_AND_CONTINUE,
                    defaultAssumptionLine(gap));
        }
        return new CanonicalPlanningGapEngine.AssessedGap(gap, ClarificationResolutionDecision.ASK_USER, "");
    }

    private static String defaultAssumptionLine(CanonicalPlanningGap g) {
        return "Assumed default for coordinator gap `"
                + g.gapId()
                + "` after repo-grounded draft — confirm or correct in-thread if this is wrong.";
    }
}
