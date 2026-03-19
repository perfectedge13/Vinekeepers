package com.vinekeepers.state.planning;

import java.util.Objects;

/**
 * Captured answer metadata after a discovery turn (for logs / snapshots).
 */
public final class DiscoveryFinding {

    private final String questionId;
    private final String normalizedAnswer;
    private final String confidenceHint;
    private final String applyTarget;

    public DiscoveryFinding(
            String questionId,
            String normalizedAnswer,
            String confidenceHint,
            String applyTarget) {
        this.questionId = questionId != null ? questionId : "";
        this.normalizedAnswer = normalizedAnswer != null ? normalizedAnswer : "";
        this.confidenceHint = confidenceHint != null ? confidenceHint : "";
        this.applyTarget = applyTarget != null ? applyTarget : "";
    }

    public String getQuestionId() {
        return questionId;
    }

    public String getNormalizedAnswer() {
        return normalizedAnswer;
    }

    public String getConfidenceHint() {
        return confidenceHint;
    }

    public String getApplyTarget() {
        return applyTarget;
    }
}
