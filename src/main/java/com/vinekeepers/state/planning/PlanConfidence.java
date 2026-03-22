package com.vinekeepers.state.planning;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Confidence and machine readiness summary for the plan (Phase C extends with readiness).
 */
public final class PlanConfidence {

    private final String level;
    private final String notes;
    private final String readinessStatus;
    private final Instant computedAt;
    private final double confidenceScore;
    private final List<String> confidenceReasons;

    public PlanConfidence(String level, String notes) {
        this(level, notes, null, null, -1.0, List.of());
    }

    public PlanConfidence(String level, String notes, String readinessStatus, Instant computedAt) {
        this(level, notes, readinessStatus, computedAt, -1.0, List.of());
    }

    public PlanConfidence(
            String level,
            String notes,
            String readinessStatus,
            Instant computedAt,
            double confidenceScore,
            List<String> confidenceReasons) {
        this.level = level;
        this.notes = notes;
        this.readinessStatus = readinessStatus;
        this.computedAt = computedAt;
        this.confidenceScore = confidenceScore;
        this.confidenceReasons = confidenceReasons != null ? List.copyOf(confidenceReasons) : List.of();
    }

    public String getLevel() {
        return level;
    }

    public String getNotes() {
        return notes;
    }

    public String getReadinessStatus() {
        return readinessStatus;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    /** 0.0–1.0 when computed; negative if not set. */
    public double getConfidenceScore() {
        return confidenceScore;
    }

    public List<String> getConfidenceReasons() {
        return confidenceReasons;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanConfidence that = (PlanConfidence) o;
        return Double.compare(that.confidenceScore, confidenceScore) == 0
                && Objects.equals(level, that.level)
                && Objects.equals(notes, that.notes)
                && Objects.equals(readinessStatus, that.readinessStatus)
                && Objects.equals(computedAt, that.computedAt)
                && confidenceReasons.equals(that.confidenceReasons);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, notes, readinessStatus, computedAt, confidenceScore, confidenceReasons);
    }
}
