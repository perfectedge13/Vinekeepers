package com.vinekeepers.profile;

import java.util.Objects;

/**
 * Optional knobs under {@code coordinatorClarification} in work profiles (post-draft clarification engine).
 */
public final class CoordinatorClarificationEnginePolicy {

    private final int maxClarificationTurns;
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
        this.maxClarificationTurns = Math.max(0, maxClarificationTurns);
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
        return new CoordinatorClarificationEnginePolicy(10, 0.45, 0.48, true, true);
    }

    public int getMaxClarificationTurns() {
        return maxClarificationTurns;
    }

    /**
     * When the clarification assessor repo-grounding score is at or above this value, non-blocking coordinator gaps may be
     * assumed instead of prompting.
     */
    public double getRepoEvidenceAskThreshold() {
        return repoEvidenceAskThreshold;
    }

    /**
     * Non-blocking gaps with {@code rankScore} below this threshold are assumed instead of prompting.
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
                && Double.compare(that.repoEvidenceAskThreshold, repoEvidenceAskThreshold) == 0
                && allowAssumeAndContinue == that.allowAssumeAndContinue
                && allowClarificationAfterCritique == that.allowClarificationAfterCritique;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                maxClarificationTurns,
                repoEvidenceAskThreshold,
                clarificationAskPriorityThreshold,
                allowAssumeAndContinue,
                allowClarificationAfterCritique);
    }
}
