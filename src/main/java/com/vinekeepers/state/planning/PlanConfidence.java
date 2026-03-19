package com.vinekeepers.state.planning;

import java.time.Instant;
import java.util.Objects;

/**
 * Confidence and machine readiness summary for the plan (Phase C extends with readiness).
 */
public final class PlanConfidence {

    private final String level;
    private final String notes;
    private final String readinessStatus;
    private final Instant computedAt;

    public PlanConfidence(String level, String notes) {
        this(level, notes, null, null);
    }

    public PlanConfidence(String level, String notes, String readinessStatus, Instant computedAt) {
        this.level = level;
        this.notes = notes;
        this.readinessStatus = readinessStatus;
        this.computedAt = computedAt;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanConfidence that = (PlanConfidence) o;
        return Objects.equals(level, that.level)
                && Objects.equals(notes, that.notes)
                && Objects.equals(readinessStatus, that.readinessStatus)
                && Objects.equals(computedAt, that.computedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, notes, readinessStatus, computedAt);
    }
}
