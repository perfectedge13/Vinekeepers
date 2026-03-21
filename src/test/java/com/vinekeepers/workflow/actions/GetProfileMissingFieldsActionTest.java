package com.vinekeepers.workflow.actions;

import com.vinekeepers.profile.TestWorkProfiles;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GetProfileMissingFieldsActionTest {

    @Test
    void reportsMissingRequiredFieldsAfterInit() {
        var reg = TestWorkProfiles.loadFromRepoConfig();
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new com.vinekeepers.state.planning.FeatureRoomStateStore(), reg);
        assertEquals(
                "OK",
                init.run(
                        null,
                        Map.of("contextId", "cx", "channelId", "ch"),
                        Map.of("profileId", "software_feature_planning")));

        GetProfileMissingFieldsAction get = new GetProfileMissingFieldsAction(store, reg);
        Object summary = get.run(null, Map.of("contextId", "cx"), Map.of());
        assertInstanceOf(String.class, summary);
        String s = (String) summary;
        assertTrue(s.contains("Still needed") || s.contains("required") || s.contains("Plan") || s.contains("Validation"));
    }

    @Test
    void allPresentAfterRequirementsNarrativeFilled() {
        var reg = TestWorkProfiles.loadFromRepoConfig();
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new com.vinekeepers.state.planning.FeatureRoomStateStore(), reg);
        init.run(
                null,
                Map.of("contextId", "cy", "channelId", "ch"),
                Map.of("profileId", "software_feature_planning"));
        UpsertArtifactSectionDataAction upsert = new UpsertArtifactSectionDataAction(store, reg);
        upsert.run(
                null,
                Map.of("contextId", "cy"),
                Map.of(
                        "artifactId",
                        "requirements_spec",
                        "sectionId",
                        "narrative",
                        "data",
                        Map.of(
                                "feature_summary", "done",
                                "acceptance_criteria", "- It works end-to-end.")));

        upsert.run(
                null,
                Map.of("contextId", "cy"),
                Map.of(
                        "artifactId",
                        "overall_plan",
                        "sectionId",
                        "outline",
                        "data",
                        Map.of("plan_body", "steps")));

        upsert.run(
                null,
                Map.of("contextId", "cy"),
                Map.of(
                        "artifactId",
                        "validation_plan",
                        "sectionId",
                        "checks",
                        "data",
                        Map.of("validation_notes", "tests")));

        GetProfileMissingFieldsAction get = new GetProfileMissingFieldsAction(store, reg);
        String s = (String) get.run(null, Map.of("contextId", "cy"), Map.of());
        assertTrue(s.contains("All required profile fields present"), s);
    }
}
