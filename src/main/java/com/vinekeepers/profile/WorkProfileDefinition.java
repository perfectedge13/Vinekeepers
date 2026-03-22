package com.vinekeepers.profile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A named work profile: ordered artifacts and their section/field schema.
 */
public final class WorkProfileDefinition {

    private final String profileId;
    private final String title;
    private final Map<String, ArtifactDefinition> artifactsById;
    private final List<ReadinessAnyOfGroup> readinessAnyOfGroups;
    /**
     * When true, planning clarification may use button/dropdown UI for explicit A-or-B style questions.
     * Default false: plain-text clarification only unless the profile opts in.
     */
    private final boolean boundedClarificationChoices;
    /**
     * When true (and {@link #boundedClarificationChoices} is true), free-text questions containing {@code or} may be
     * split into bounded button choices via heuristic. Default false: structured choices only when explicitly marked
     * bounded in the ledger / gap flow.
     */
    private final boolean inferBoundedChoiceFromOrInText;
    /** Substrings that must not appear in user-visible packet fields (declarative quality gate). */
    private final List<String> packetQualityForbiddenSubstrings;

    public WorkProfileDefinition(String profileId, String title, List<ArtifactDefinition> artifacts) {
        this(profileId, title, artifacts, List.of(), false);
    }

    public WorkProfileDefinition(
            String profileId, String title, List<ArtifactDefinition> artifacts, List<ReadinessAnyOfGroup> readinessAnyOfGroups) {
        this(profileId, title, artifacts, readinessAnyOfGroups, false);
    }

    public WorkProfileDefinition(
            String profileId,
            String title,
            List<ArtifactDefinition> artifacts,
            List<ReadinessAnyOfGroup> readinessAnyOfGroups,
            boolean boundedClarificationChoices) {
        this(profileId, title, artifacts, readinessAnyOfGroups, boundedClarificationChoices, List.of());
    }

    public WorkProfileDefinition(
            String profileId,
            String title,
            List<ArtifactDefinition> artifacts,
            List<ReadinessAnyOfGroup> readinessAnyOfGroups,
            boolean boundedClarificationChoices,
            List<String> packetQualityForbiddenSubstrings) {
        this(
                profileId,
                title,
                artifacts,
                readinessAnyOfGroups,
                boundedClarificationChoices,
                false,
                packetQualityForbiddenSubstrings);
    }

    public WorkProfileDefinition(
            String profileId,
            String title,
            List<ArtifactDefinition> artifacts,
            List<ReadinessAnyOfGroup> readinessAnyOfGroups,
            boolean boundedClarificationChoices,
            boolean inferBoundedChoiceFromOrInText,
            List<String> packetQualityForbiddenSubstrings) {
        this.profileId = Objects.requireNonNull(profileId, "profileId").trim();
        this.title = title != null ? title : "";
        Map<String, ArtifactDefinition> m = new LinkedHashMap<>();
        if (artifacts != null) {
            for (ArtifactDefinition a : artifacts) {
                m.put(a.getArtifactId(), a);
            }
        }
        this.artifactsById = Map.copyOf(m);
        this.readinessAnyOfGroups = readinessAnyOfGroups != null ? List.copyOf(readinessAnyOfGroups) : List.of();
        this.boundedClarificationChoices = boundedClarificationChoices;
        this.inferBoundedChoiceFromOrInText = inferBoundedChoiceFromOrInText;
        this.packetQualityForbiddenSubstrings =
                packetQualityForbiddenSubstrings != null ? List.copyOf(packetQualityForbiddenSubstrings) : List.of();
    }

    public String getProfileId() {
        return profileId;
    }

    public String getTitle() {
        return title;
    }

    public Map<String, ArtifactDefinition> getArtifactsById() {
        return artifactsById;
    }

    public Optional<ArtifactDefinition> getArtifact(String artifactId) {
        if (artifactId == null || artifactId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(artifactsById.get(artifactId));
    }

    public Optional<SectionDefinition> findSection(String artifactId, String sectionId) {
        return getArtifact(artifactId).flatMap(a -> {
            if (sectionId == null || sectionId.isBlank()) {
                return Optional.empty();
            }
            return a.getSections().stream().filter(s -> s.getSectionId().equals(sectionId)).findFirst();
        });
    }

    public List<ReadinessAnyOfGroup> getReadinessAnyOfGroups() {
        return readinessAnyOfGroups;
    }

    public boolean isBoundedClarificationChoicesEnabled() {
        return boundedClarificationChoices;
    }

    public boolean isInferBoundedChoiceFromOrInTextEnabled() {
        return inferBoundedChoiceFromOrInText;
    }

    public List<String> getPacketQualityForbiddenSubstrings() {
        return packetQualityForbiddenSubstrings;
    }

    public boolean hasDeclarativeReadiness() {
        if (!readinessAnyOfGroups.isEmpty()) {
            return true;
        }
        for (ArtifactDefinition a : artifactsById.values()) {
            for (SectionDefinition sec : a.getSections()) {
                for (FieldDefinition f : sec.getFields()) {
                    if (f.hasReadinessConstraints()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
