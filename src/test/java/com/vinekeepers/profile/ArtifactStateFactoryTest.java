package com.vinekeepers.profile;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactStateFactoryTest {

    @Test
    void emptyArtifactsMatchProfileSections() {
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        WorkProfileDefinition def = reg.get("software_feature_planning").orElseThrow();
        var artifacts = ArtifactStateFactory.emptyArtifacts(def);
        assertEquals(def.getArtifactsById().size(), artifacts.size());
        assertTrue(artifacts.get("requirements_spec").getSectionsById().containsKey("narrative"));
        assertEquals(
                SectionState.STATUS_EMPTY,
                artifacts.get("requirements_spec").getSectionsById().get("narrative").getStatus());
    }
}
