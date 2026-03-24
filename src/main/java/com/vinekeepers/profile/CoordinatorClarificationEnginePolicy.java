package com.vinekeepers.profile;

import java.util.Objects;

/**
 * Optional knobs under {@code coordinatorClarification} in work profiles (post-draft clarification engine).
 */
public final class CoordinatorClarificationEnginePolicy {

    private final int maxClarificationTurns;
    private final int maxClarificationTurnsPerGap;
    private final int maxAutonomousRedraftsBeforeAsk;
    private final double clarificationConfidenceThreshold;
    private final double repoEvidenceAskThreshold;
    private final double clarificationAskPriorityThreshold;
    private final boolean allowAssumeAndContinue;
    private final boolean allowClarificationAfterCritique;

    public CoordinatorClarificationEnginePolicy(
            int maxClarificationTurns,
            double repoEvidenceAskThreshold,
            double clarificationAskPriorityThreshold,
            boolean allowAssumeAndContinue,
            boolean allowClarificationAfterCritique) {
        this(
                maxClarificationTurns,
                2,
                2,
                0.72,
                repoEvidenceAskThreshold,
                clarificationAskPriorityThreshold,
                allowAssumeAndContinue,
                allowClarificationAfterCritique);
    }

    public CoordinatorClarificationEnginePolicy(
            int maxClarificationTurns,
            int maxClarificationTurnsPerGap,
            int maxAutonomousRedraftsBeforeAsk,
            double clarificationConfidenceThreshold,
            double repoEvidenceAskThreshold,
            double clarificationAskPriorityThreshold,
            boolean allowAssumeAndContinue,
            boolean allowClarificationAfterCritique) {
        this.maxClarificationTurns = Math.max(0, maxClarificationTurns);
        this.maxClarificationTurnsPerGap = Math.max(0, maxClarificationTurnsPerGap);
        this.maxAutonomousRedraftsBeforeAsk = Math.max(0, maxAutonomousRedraftsBeforeAsk);
        this.clarificationConfidenceThreshold =
                clarificationConfidenceThreshold >= 0 && clarificationConfidenceThreshold <= 1
                        ? clarificationConfidenceThreshold
                        : 0.72;
        this.repoEvidenceAskThreshold =
                repoEvidenceAskThreshold >= 0 && repoEvidenceAskThreshold <= 1
                        ? repoEvidenceAskThreshold
                        : 0.45;
        this.clarificationAskPriorityThreshold =
                clarificationAskPriorityThreshold >= 0 && clarificationAskPriorityThreshold <= 1
                        ? clarificationAskPriorityThreshold
                        : 0.48;
        this.allowAssumeAndContinue = allowAssumeAndContinue;
        this.allowClarificationAfterCritique = allowClarificationAfterCritique;
    }

    public static CoordinatorClarificationEnginePolicy defaultPolicy() {
        return new CoordinatorClarificationEnginePolicy(10, 2, 2, 0.72, 0.45, 0.48, true, true);
    }

    public int getMaxClarificationTurns() {
        return maxClarificationTurns;
    }

    public int getMaxClarificationTurnsPerGap() {
        return maxClarificationTurnsPerGap;
    }

    public int getMaxAutonomousRedraftsBeforeAsk() {
        return maxAutonomousRedraftsBeforeAsk;
    }

    /**
     * Confidence threshold for stopping the clarification/redraft loop and continuing with the saved draft.
     */
    public double getClarificationConfidenceThreshold() {
        return clarificationConfidenceThreshold;
    }

    /**
     * When the clarification assessor repo-grounding score is at or above this value, non-blocking coordinator gaps may be
     * assumed instead of prompting.
     */
    public double getRepoEvidenceAskThreshold() {
        return repoEvidenceAskThreshold;
    }

    /**
     * Legacy profile knob; canonical_v1 resolution no longer ranks gaps by score. Retained for YAML compatibility.
     */
    public double getClarificationAskPriorityThreshold() {
        return clarificationAskPriorityThreshold;
    }

    public boolean isAllowAssumeAndContinue() {
        return allowAssumeAndContinue;
    }

    public boolean isAllowClarificationAfterCritique() {
        return allowClarificationAfterCritique;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CoordinatorClarificationEnginePolicy that = (CoordinatorClarificationEnginePolicy) o;
        return maxClarificationTurns == that.maxClarificationTurns
                && maxClarificationTurnsPerGap == that.maxClarificationTurnsPerGap
                && maxAutonomousRedraftsBeforeAsk == that.maxAutonomousRedraftsBeforeAsk
                && Double.compare(that.clarificationConfidenceThreshold, clarificationConfidenceThreshold) == 0
                && Double.compare(that.repoEvidenceAskThreshold, repoEvidenceAskThreshold) == 0
                && Double.compare(that.clarificationAskPriorityThreshold, clarificationAskPriorityThreshold) == 0
                && allowAssumeAndContinue == that.allowAssumeAndContinue
                && allowClarificationAfterCritique == that.allowClarificationAfterCritique;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                maxClarificationTurns,
                maxClarificationTurnsPerGap,
                maxAutonomousRedraftsBeforeAsk,
                clarificationConfidenceThreshold,
                repoEvidenceAskThreshold,
                clarificationAskPriorityThreshold,
                allowAssumeAndContinue,
                allowClarificationAfterCritique);
    }
}
