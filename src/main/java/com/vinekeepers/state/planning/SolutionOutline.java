package com.vinekeepers.state.planning;

import java.util.Objects;

public final class SolutionOutline {

    private final String summary;

    public SolutionOutline(String summary) {
        this.summary = summary != null ? summary : "";
    }

    public String getSummary() {
        return summary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SolutionOutline that = (SolutionOutline) o;
        return summary.equals(that.summary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(summary);
    }
}
