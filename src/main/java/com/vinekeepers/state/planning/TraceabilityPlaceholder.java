package com.vinekeepers.state.planning;

import java.util.Objects;

public final class TraceabilityPlaceholder {

    private final String text;

    public TraceabilityPlaceholder(String text) {
        this.text = text != null ? text : "";
    }

    public String getText() {
        return text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TraceabilityPlaceholder that = (TraceabilityPlaceholder) o;
        return text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }
}
