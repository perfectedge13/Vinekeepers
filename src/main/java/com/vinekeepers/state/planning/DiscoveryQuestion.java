package com.vinekeepers.state.planning;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * One prioritized discovery prompt with optional artifact apply target.
 */
public final class DiscoveryQuestion {

    private final String questionId;
    private final String gapId;
    private final String askRole;
    private final String priority;
    private final String prompt;
    private final String applyKind;
    private final String artifactId;
    private final String sectionId;
    private final String fieldId;
    private final String applyMode;

    @JsonCreator
    public DiscoveryQuestion(
            @JsonProperty("questionId") String questionId,
            @JsonProperty("gapId") String gapId,
            @JsonProperty("askRole") String askRole,
            @JsonProperty("priority") String priority,
            @JsonProperty("prompt") String prompt,
            @JsonProperty("applyKind") String applyKind,
            @JsonProperty("artifactId") String artifactId,
            @JsonProperty("sectionId") String sectionId,
            @JsonProperty("fieldId") String fieldId,
            @JsonProperty("applyMode") String applyMode) {
        this.questionId = Objects.requireNonNull(questionId, "questionId");
        this.gapId = gapId != null ? gapId : "";
        this.askRole = askRole != null ? askRole : "orchestrator";
        this.priority = priority != null ? priority : "MEDIUM";
        this.prompt = prompt != null ? prompt : "";
        this.applyKind = applyKind != null ? applyKind : "NONE";
        this.artifactId = artifactId != null ? artifactId : "";
        this.sectionId = sectionId != null ? sectionId : "";
        this.fieldId = fieldId != null ? fieldId : "";
        this.applyMode = applyMode != null && !applyMode.isBlank() ? applyMode : "replace";
    }

    public String getQuestionId() {
        return questionId;
    }

    public String getGapId() {
        return gapId;
    }

    public String getAskRole() {
        return askRole;
    }

    public String getPriority() {
        return priority;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getApplyKind() {
        return applyKind;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getSectionId() {
        return sectionId;
    }

    public String getFieldId() {
        return fieldId;
    }

    public String getApplyMode() {
        return applyMode;
    }
}
