package com.vinekeepers.state.planning;

import java.time.Instant;
import java.util.Objects;

public final class RequirementEntry {

    private final String id;
    private final String text;
    private final Instant addedAt;

    public RequirementEntry(String id, String text, Instant addedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.text = text != null ? text : "";
        this.addedAt = addedAt != null ? addedAt : Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RequirementEntry that = (RequirementEntry) o;
        return id.equals(that.id) && text.equals(that.text) && addedAt.equals(that.addedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, text, addedAt);
    }
}
