package com.vinekeepers.profile;

import java.util.List;
import java.util.Objects;

/**
 * Declares an artifact (document bucket) within a work profile.
 */
public final class ArtifactDefinition {

    private final String artifactId;
    private final String title;
    private final List<String> ownerRoles;
    private final boolean requiredForApproval;
    private final List<SectionDefinition> sections;

    public ArtifactDefinition(
            String artifactId,
            String title,
            List<String> ownerRoles,
            boolean requiredForApproval,
            List<SectionDefinition> sections) {
        this.artifactId = Objects.requireNonNull(artifactId, "artifactId").trim();
        this.title = title != null ? title : "";
        this.ownerRoles = ownerRoles != null ? List.copyOf(ownerRoles) : List.of();
        this.requiredForApproval = requiredForApproval;
        this.sections = sections != null ? List.copyOf(sections) : List.of();
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getOwnerRoles() {
        return ownerRoles;
    }

    public boolean isRequiredForApproval() {
        return requiredForApproval;
    }

    public List<SectionDefinition> getSections() {
        return sections;
    }
}
