package com.vinekeepers.workflow.actions;

import com.vinekeepers.profile.SectionState;
import com.vinekeepers.profile.TestWorkProfiles;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UpsertArtifactSectionDataActionTest {

    private FeaturePlanStateStore planStore;
    private com.vinekeepers.profile.WorkProfileRegistry registry;

    @BeforeEach
    void setUp() {
        planStore = new FeaturePlanStateStore();
        registry = TestWorkProfiles.loadFromRepoConfig();
    }

    @Test
    void upsertReplacesValuesAndSetsDraft() {
        var init = new InitializeFeaturePlanStateAction(planStore, new com.vinekeepers.state.planning.FeatureRoomStateStore(), registry);
        assertEquals(
                "OK",
                init.run(
                        null,
                        Map.of("contextId", "c1", "channelId", "room1"),
                        Map.of("profileId", "software_feature_planning")));

        UpsertArtifactSectionDataAction upsert = new UpsertArtifactSectionDataAction(planStore, registry);
        Object r = upsert.run(
                null,
                Map.of("contextId", "c1", "codeChange", "Build feature X"),
                Map.of(
                        "artifactId",
                        "requirements_spec",
                        "sectionId",
                        "narrative",
                        "mode",
                        "replace",
                        "data",
                        Map.of("feature_summary", "{{codeChange}}")));
        assertEquals("OK", r);
        var plan = planStore.getByContextId("c1").orElseThrow();
        SectionState sec = plan.getArtifacts().get("requirements_spec").getSectionsById().get("narrative");
        assertEquals("Build feature X", sec.getValues().get("feature_summary"));
        assertEquals(SectionState.STATUS_DRAFT, sec.getStatus());
    }

    @Test
    void rejectsUnknownSection() {
        var init = new InitializeFeaturePlanStateAction(planStore, new com.vinekeepers.state.planning.FeatureRoomStateStore(), registry);
        init.run(
                null,
                Map.of("contextId", "c2", "channelId", "r"),
                Map.of("profileId", "software_feature_planning"));
        UpsertArtifactSectionDataAction upsert = new UpsertArtifactSectionDataAction(planStore, registry);
        Object r = upsert.run(
                null,
                Map.of("contextId", "c2"),
                Map.of(
                        "artifactId",
                        "requirements_spec",
                        "sectionId",
                        "nope",
                        "data",
                        Map.of("x", "y")));
        assertTrue(r.toString().contains("Unknown artifact/section"));
    }
}
