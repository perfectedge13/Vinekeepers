package com.vinekeepers.state.planning;

import java.util.Objects;

public final class ProjectContextSummary {

    private final String text;

    public ProjectContextSummary(String text) {
        this.text = text != null ? text : "";
    }

    public String getText() {
        return text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectContextSummary that = (ProjectContextSummary) o;
        return text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }
}
