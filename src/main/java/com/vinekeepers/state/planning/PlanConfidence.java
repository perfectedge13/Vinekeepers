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
    /** Count of structured, captured planning facts (requirements, artifacts, decisions, etc.). */
    private final int structuredKnownFactCount;
    /** Count of material unknowns (open discovery gaps, unresolved questions, blocking issues, …). */
    private final int materialUnknownCount;
    /** Short labels for material unknowns (JSON-friendly, capped at computation time). */
    private final List<String> materialUnknownLabels;

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
        this(
                level,
                notes,
                readinessStatus,
                computedAt,
                confidenceScore,
                confidenceReasons,
                0,
                0,
                List.of());
    }

    public PlanConfidence(
            String level,
            String notes,
            String readinessStatus,
            Instant computedAt,
            double confidenceScore,
            List<String> confidenceReasons,
            int structuredKnownFactCount,
            int materialUnknownCount,
            List<String> materialUnknownLabels) {
        this.level = level;
        this.notes = notes;
        this.readinessStatus = readinessStatus;
        this.computedAt = computedAt;
        this.confidenceScore = confidenceScore;
        this.confidenceReasons = confidenceReasons != null ? List.copyOf(confidenceReasons) : List.of();
        this.structuredKnownFactCount = Math.max(0, structuredKnownFactCount);
        this.materialUnknownCount = Math.max(0, materialUnknownCount);
        this.materialUnknownLabels =
                materialUnknownLabels != null ? List.copyOf(materialUnknownLabels) : List.of();
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

    public int getStructuredKnownFactCount() {
        return structuredKnownFactCount;
    }

    public int getMaterialUnknownCount() {
        return materialUnknownCount;
    }

    public List<String> getMaterialUnknownLabels() {
        return materialUnknownLabels;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanConfidence that = (PlanConfidence) o;
        return Double.compare(that.confidenceScore, confidenceScore) == 0
                && structuredKnownFactCount == that.structuredKnownFactCount
                && materialUnknownCount == that.materialUnknownCount
                && Objects.equals(level, that.level)
                && Objects.equals(notes, that.notes)
                && Objects.equals(readinessStatus, that.readinessStatus)
                && Objects.equals(computedAt, that.computedAt)
                && confidenceReasons.equals(that.confidenceReasons)
                && materialUnknownLabels.equals(that.materialUnknownLabels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                level,
                notes,
                readinessStatus,
                computedAt,
                confidenceScore,
                confidenceReasons,
                structuredKnownFactCount,
                materialUnknownCount,
                materialUnknownLabels);
    }
}
