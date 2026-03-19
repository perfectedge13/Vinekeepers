package com.vinekeepers.state.planning;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Typed unresolved discovery item for guided planning (Phase B).
 */
public final class DiscoveryGap {

    private final String gapId;
    private final String kind;
    private final String artifactId;
    private final String sectionId;
    private final String fieldId;
    private final String reason;
    private final String severity;
    private final String status;
    private final String source;

    @JsonCreator
    public DiscoveryGap(
            @JsonProperty("gapId") String gapId,
            @JsonProperty("kind") String kind,
            @JsonProperty("artifactId") String artifactId,
            @JsonProperty("sectionId") String sectionId,
            @JsonProperty("fieldId") String fieldId,
            @JsonProperty("reason") String reason,
            @JsonProperty("severity") String severity,
            @JsonProperty("status") String status,
            @JsonProperty("source") String source) {
        this.gapId = Objects.requireNonNull(gapId, "gapId");
        this.kind = kind != null ? kind : "";
        this.artifactId = artifactId != null ? artifactId : "";
        this.sectionId = sectionId != null ? sectionId : "";
        this.fieldId = fieldId != null ? fieldId : "";
        this.reason = reason != null ? reason : "";
        this.severity = severity != null ? severity : "MEDIUM";
        this.status = status != null ? status : "OPEN";
        this.source = source != null ? source : "";
    }

    public String getGapId() {
        return gapId;
    }

    public String getKind() {
        return kind;
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

    public String getReason() {
        return reason;
    }

    public String getSeverity() {
        return severity;
    }

    public String getStatus() {
        return status;
    }

    public String getSource() {
        return source;
    }
}
