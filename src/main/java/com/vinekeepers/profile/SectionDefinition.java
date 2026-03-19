package com.vinekeepers.profile;

import java.util.List;
import java.util.Objects;

/**
 * Declares a section within a profile artifact.
 */
public final class SectionDefinition {

    private final String sectionId;
    private final String title;
    private final boolean repeatable;
    private final boolean required;
    private final List<FieldDefinition> fields;

    public SectionDefinition(
            String sectionId,
            String title,
            boolean repeatable,
            boolean required,
            List<FieldDefinition> fields) {
        this.sectionId = Objects.requireNonNull(sectionId, "sectionId").trim();
        this.title = title != null ? title : "";
        this.repeatable = repeatable;
        this.required = required;
        this.fields = fields != null ? List.copyOf(fields) : List.of();
    }

    public String getSectionId() {
        return sectionId;
    }

    public String getTitle() {
        return title;
    }

    public boolean isRepeatable() {
        return repeatable;
    }

    public boolean isRequired() {
        return required;
    }

    public List<FieldDefinition> getFields() {
        return fields;
    }
}
