package com.vinekeepers.profile;

import java.util.List;
import java.util.Objects;

/**
 * Declarative readiness constraint on a single artifact/section/field path (config-driven).
 */
public final class ReadinessPathRule {

    private final String artifactId;
    private final String sectionId;
    private final String fieldId;
    private final Integer minWords;
    private final Double maxEchoOverlapWithRequest;
    private final Integer echoWordSlack;
    private final boolean skipIfBlank;
    private final List<String> readinessChecks;

    public ReadinessPathRule(
            String artifactId,
            String sectionId,
            String fieldId,
            Integer minWords,
            Double maxEchoOverlapWithRequest,
            Integer echoWordSlack,
            boolean skipIfBlank,
            List<String> readinessChecks) {
        this.artifactId = artifactId != null ? artifactId.trim() : "";
        this.sectionId = sectionId != null ? sectionId.trim() : "";
        this.fieldId = fieldId != null ? fieldId.trim() : "";
        this.minWords = minWords;
        this.maxEchoOverlapWithRequest = maxEchoOverlapWithRequest;
        this.echoWordSlack = echoWordSlack;
        this.skipIfBlank = skipIfBlank;
        this.readinessChecks = readinessChecks != null ? List.copyOf(readinessChecks) : List.of();
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

    public Integer getMinWords() {
        return minWords;
    }

    public Double getMaxEchoOverlapWithRequest() {
        return maxEchoOverlapWithRequest;
    }

    public Integer getEchoWordSlack() {
        return echoWordSlack;
    }

    public boolean isSkipIfBlank() {
        return skipIfBlank;
    }

    public List<String> getReadinessChecks() {
        return readinessChecks;
    }

    public boolean hasAnyConstraint() {
        return minWords != null
                || maxEchoOverlapWithRequest != null
                || !readinessChecks.isEmpty();
    }

    public String pathLabel() {
        return artifactId + "/" + sectionId + "/" + fieldId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ReadinessPathRule that = (ReadinessPathRule) o;
        return skipIfBlank == that.skipIfBlank
                && Objects.equals(artifactId, that.artifactId)
                && Objects.equals(sectionId, that.sectionId)
                && Objects.equals(fieldId, that.fieldId)
                && Objects.equals(minWords, that.minWords)
                && Objects.equals(maxEchoOverlapWithRequest, that.maxEchoOverlapWithRequest)
                && Objects.equals(echoWordSlack, that.echoWordSlack)
                && Objects.equals(readinessChecks, that.readinessChecks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                artifactId,
                sectionId,
                fieldId,
                minWords,
                maxEchoOverlapWithRequest,
                echoWordSlack,
                skipIfBlank,
                readinessChecks);
    }
}
