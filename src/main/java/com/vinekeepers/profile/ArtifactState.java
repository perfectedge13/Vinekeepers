package com.vinekeepers.profile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime state for one artifact (collection of section states).
 */
public final class ArtifactState {

    private final String artifactId;
    private final Map<String, SectionState> sectionsById;

    public ArtifactState(String artifactId, Map<String, SectionState> sectionsById) {
        this.artifactId = Objects.requireNonNull(artifactId, "artifactId").trim();
        if (sectionsById == null || sectionsById.isEmpty()) {
            this.sectionsById = Map.of();
        } else {
            this.sectionsById = Map.copyOf(new LinkedHashMap<>(sectionsById));
        }
    }

    public String getArtifactId() {
        return artifactId;
    }

    public Map<String, SectionState> getSectionsById() {
        return sectionsById;
    }

    public ArtifactState withSection(String sectionId, SectionState section) {
        Map<String, SectionState> m = new LinkedHashMap<>(sectionsById);
        m.put(section.getSectionId(), Objects.requireNonNull(section));
        return new ArtifactState(artifactId, m);
    }
}
