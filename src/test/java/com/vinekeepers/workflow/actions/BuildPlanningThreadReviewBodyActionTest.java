package com.vinekeepers.workflow.actions;

import com.vinekeepers.profile.WorkProfileLoader;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildPlanningThreadReviewBodyActionTest {

    @Test
    void whenPacketAlreadyPosted_usesConciseReviewBodyWithoutFullPacketHeadings() {
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new FeatureRoomStateStore(), reg);
        assertEquals(
                "OK",
                init.run(
                        null,
                        Map.of("contextId", "concise-ctx", "channelId", "ch"),
                        Map.of("profileId", "software_feature_planning")));

        var upsert = new UpsertArtifactSectionDataAction(store, reg);
        assertEquals(
                "OK",
                upsert.run(
                        null,
                        Map.of("contextId", "concise-ctx"),
                        Map.of(
                                "artifactId",
                                "overall_plan",
                                "sectionId",
                                "outline",
                                "mode",
                                "replace",
                                "data",
                                Map.of("plan_body", "Would appear in full packet."))));

        var build = new BuildPlanningThreadReviewBodyAction(store);
        @SuppressWarnings("unchecked")
        Map<String, Object> spread =
                (Map<String, Object>)
                        build.run(
                                null,
                                Map.of("contextId", "concise-ctx", "planningPacketPosted", "true"),
                                Map.of());
        assertEquals("", spread.get("planningThreadReviewBuildError"));
        String body = (String) spread.get("planningThreadReviewBody");
        assertTrue(body.contains("source of truth"));
        assertTrue(body.contains("**Planning packet**"));
        assertFalse(body.contains("**Proposed behavior / outline**"));
    }

    @Test
    void whenFullBodyExceedsWorkflowCap_appendsTruncationNotice() {
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new FeatureRoomStateStore(), reg);
        String hugeRequest = "R".repeat(20_000);
        assertEquals(
                "OK",
                init.run(
                        null,
                        Map.of("contextId", "cap-ctx", "channelId", "ch", "codeChange", hugeRequest),
                        Map.of("profileId", "software_feature_planning")));

        var build = new BuildPlanningThreadReviewBodyAction(store);
        @SuppressWarnings("unchecked")
        Map<String, Object> spread =
                (Map<String, Object>) build.run(null, Map.of("contextId", "cap-ctx"), Map.of());
        assertEquals("", spread.get("planningThreadReviewBuildError"));
        String body = (String) spread.get("planningThreadReviewBody");
        assertTrue(body.length() < hugeRequest.length(), "expected truncation vs raw request");
        assertTrue(body.contains("_(Planning thread review truncated to fit the workflow cap of 12000 characters.)_"));
    }
}
