package com.vinekeepers.profile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds empty {@link ArtifactState} trees from a {@link WorkProfileDefinition}.
 */
public final class ArtifactStateFactory {

    private ArtifactStateFactory() {}

    public static Map<String, ArtifactState> emptyArtifacts(WorkProfileDefinition profile) {
        if (profile == null) {
            return Map.of();
        }
        Map<String, ArtifactState> out = new LinkedHashMap<>();
        for (ArtifactDefinition a : profile.getArtifactsById().values()) {
            Map<String, SectionState> sections = new LinkedHashMap<>();
            for (SectionDefinition s : a.getSections()) {
                sections.put(
                        s.getSectionId(),
                        new SectionState(s.getSectionId(), SectionState.STATUS_EMPTY, Map.of(), List.of()));
            }
            out.put(a.getArtifactId(), new ArtifactState(a.getArtifactId(), sections));
        }
        return Map.copyOf(out);
    }
}
