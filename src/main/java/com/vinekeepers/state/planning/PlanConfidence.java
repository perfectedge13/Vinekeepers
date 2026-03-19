package com.vinekeepers.state.planning;

import java.util.Objects;

/**
 * Optional lightweight confidence placeholder for the plan.
 */
public final class PlanConfidence {

    private final String level;
    private final String notes;

    public PlanConfidence(String level, String notes) {
        this.level = level;
        this.notes = notes;
    }

    public String getLevel() {
        return level;
    }

    public String getNotes() {
        return notes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanConfidence that = (PlanConfidence) o;
        return Objects.equals(level, that.level) && Objects.equals(notes, that.notes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, notes);
    }
}
