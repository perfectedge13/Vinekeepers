package com.vinekeepers.profile;

import java.util.List;
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
    private final Integer minWords;
    private final Double maxEchoOverlapWithRequest;
    private final Integer echoWordSlack;
    private final boolean skipReadinessIfBlank;
    private final List<String> readinessChecks;

    public FieldDefinition(String fieldId, String label, String type, boolean required, String promptHint) {
        this(fieldId, label, type, required, promptHint, null, null, null, false, List.of());
    }

    public FieldDefinition(
            String fieldId,
            String label,
            String type,
            boolean required,
            String promptHint,
            Integer minWords,
            Double maxEchoOverlapWithRequest,
            Integer echoWordSlack,
            boolean skipReadinessIfBlank,
            List<String> readinessChecks) {
        this.fieldId = Objects.requireNonNull(fieldId, "fieldId").trim();
        this.label = label != null ? label : "";
        this.type = type != null && !type.isBlank() ? type.trim() : "text";
        this.required = required;
        this.promptHint = promptHint != null ? promptHint : "";
        this.minWords = minWords;
        this.maxEchoOverlapWithRequest = maxEchoOverlapWithRequest;
        this.echoWordSlack = echoWordSlack;
        this.skipReadinessIfBlank = skipReadinessIfBlank;
        this.readinessChecks = readinessChecks != null ? List.copyOf(readinessChecks) : List.of();
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

    public Integer getMinWords() {
        return minWords;
    }

    public Double getMaxEchoOverlapWithRequest() {
        return maxEchoOverlapWithRequest;
    }

    public Integer getEchoWordSlack() {
        return echoWordSlack;
    }

    public boolean isSkipReadinessIfBlank() {
        return skipReadinessIfBlank;
    }

    public List<String> getReadinessChecks() {
        return readinessChecks;
    }

    /** True when this field participates in declarative readiness (beyond required-for-approval). */
    public boolean hasReadinessConstraints() {
        return minWords != null
                || maxEchoOverlapWithRequest != null
                || !readinessChecks.isEmpty();
    }
}
