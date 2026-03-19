package com.vinekeepers.profile;

import java.util.Objects;

/**
 * Declares a single field within a profile section (config-driven).
 */
public final class FieldDefinition {

    private final String fieldId;
    private final String label;
    private final String type;
    private final boolean required;
    private final String promptHint;

    public FieldDefinition(String fieldId, String label, String type, boolean required, String promptHint) {
        this.fieldId = Objects.requireNonNull(fieldId, "fieldId").trim();
        this.label = label != null ? label : "";
        this.type = type != null && !type.isBlank() ? type.trim() : "text";
        this.required = required;
        this.promptHint = promptHint != null ? promptHint : "";
    }

    public String getFieldId() {
        return fieldId;
    }

    public String getLabel() {
        return label;
    }

    public String getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    public String getPromptHint() {
        return promptHint;
    }
}
