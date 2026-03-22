package com.vinekeepers.state.planning;

import java.util.Objects;

/** Normalized 0.0–1.0 rubric dimension scores derived from critique and governance signals. */
public final class PlanCritiqueRubricScores {

    private final double completeness;
    private final double consistency;
    private final double clarity;
    private final double repoAlignment;
    private final double plausibility;
    private final double testability;
    private final double approvalReadiness;

    public PlanCritiqueRubricScores(
            double completeness,
            double consistency,
            double clarity,
            double repoAlignment,
            double plausibility,
            double testability,
            double approvalReadiness) {
        this.completeness = clamp01(completeness);
        this.consistency = clamp01(consistency);
        this.clarity = clamp01(clarity);
        this.repoAlignment = clamp01(repoAlignment);
        this.plausibility = clamp01(plausibility);
        this.testability = clamp01(testability);
        this.approvalReadiness = clamp01(approvalReadiness);
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, v));
    }

    public double getCompleteness() {
        return completeness;
    }

    public double getConsistency() {
        return consistency;
    }

    public double getClarity() {
        return clarity;
    }

    public double getRepoAlignment() {
        return repoAlignment;
    }

    public double getPlausibility() {
        return plausibility;
    }

    public double getTestability() {
        return testability;
    }

    public double getApprovalReadiness() {
        return approvalReadiness;
    }

    /** Simple mean of all seven dimensions. */
    public double meanScore() {
        return (completeness
                        + consistency
                        + clarity
                        + repoAlignment
                        + plausibility
                        + testability
                        + approvalReadiness)
                / 7.0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanCritiqueRubricScores that = (PlanCritiqueRubricScores) o;
        return Double.compare(that.completeness, completeness) == 0
                && Double.compare(that.consistency, consistency) == 0
                && Double.compare(that.clarity, clarity) == 0
                && Double.compare(that.repoAlignment, repoAlignment) == 0
                && Double.compare(that.plausibility, plausibility) == 0
                && Double.compare(that.testability, testability) == 0
                && Double.compare(that.approvalReadiness, approvalReadiness) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                completeness,
                consistency,
                clarity,
                repoAlignment,
                plausibility,
                testability,
                approvalReadiness);
    }
}
