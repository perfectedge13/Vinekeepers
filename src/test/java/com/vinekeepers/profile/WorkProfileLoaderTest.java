package com.vinekeepers.profile;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorkProfileLoaderTest {

    @Test
    void loadsSoftwareFeaturePlanningFromRepoConfig() {
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        assertFalse(reg.isEmpty());
        WorkProfileDefinition def = reg.get("software_feature_planning").orElseThrow();
        assertEquals("software_feature_planning", def.getProfileId());
        assertTrue(def.getArtifactsById().containsKey("requirements_spec"));
        assertTrue(def.findSection("requirements_spec", "narrative").isPresent());
        assertTrue(def.findSection("requirements_spec", "narrative").orElseThrow().getFields().stream()
                .anyMatch(f -> "feature_summary".equals(f.getFieldId())));
    }

    @Test
    void softwareFeaturePlanningV2LoadsCanonicalCoordinatorClarification() {
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        WorkProfileDefinition v2 = reg.get("software_feature_planning_v2").orElseThrow();
        assertTrue(v2.getCoordinatorClarification().isCanonicalV1());
        assertTrue(v2.getCoordinatorClarification().getGaps().isEmpty());
    }
}
