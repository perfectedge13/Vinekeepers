package com.vinekeepers.state.planning;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * A draft value for a profile artifact field with confidence and how the workflow should treat it.
 */
public final class PlanningProposal {

    private final String proposalId;
    private final String artifactId;
    private final String sectionId;
    private final String fieldId;
    private final String proposedValue;
    /** HIGH, MEDIUM, or LOW */
    private final String confidence;
    /** AUTO_APPLY or CONFIRM */
    private final String interaction;
    private final List<String> sources;
    private final String reasoningSummary;
    private final String suggestedUserQuestion;

    @JsonCreator
    public PlanningProposal(
            @JsonProperty("proposalId") String proposalId,
            @JsonProperty("artifactId") String artifactId,
            @JsonProperty("sectionId") String sectionId,
            @JsonProperty("fieldId") String fieldId,
            @JsonProperty("proposedValue") String proposedValue,
            @JsonProperty("confidence") String confidence,
            @JsonProperty("interaction") String interaction,
            @JsonProperty("sources") List<String> sources,
            @JsonProperty("reasoningSummary") String reasoningSummary,
            @JsonProperty("suggestedUserQuestion") String suggestedUserQuestion) {
        this.proposalId = Objects.requireNonNull(proposalId, "proposalId");
        this.artifactId = artifactId != null ? artifactId : "";
        this.sectionId = sectionId != null ? sectionId : "";
        this.fieldId = fieldId != null ? fieldId : "";
        this.proposedValue = proposedValue != null ? proposedValue : "";
        this.confidence = confidence != null ? confidence : "MEDIUM";
        this.interaction = interaction != null ? interaction : "CONFIRM";
        this.sources = sources != null ? List.copyOf(sources) : List.of();
        this.reasoningSummary = reasoningSummary != null ? reasoningSummary : "";
        this.suggestedUserQuestion = suggestedUserQuestion != null ? suggestedUserQuestion : "";
    }

    public String getProposalId() {
        return proposalId;
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

    public String getProposedValue() {
        return proposedValue;
    }

    public String getConfidence() {
        return confidence;
    }

    public String getInteraction() {
        return interaction;
    }

    public List<String> getSources() {
        return sources;
    }

    public String getReasoningSummary() {
        return reasoningSummary;
    }

    public String getSuggestedUserQuestion() {
        return suggestedUserQuestion;
    }
}
