package com.vinekeepers.workflow.actions;

import com.vinekeepers.profile.WorkProfileLoader;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildRolePlanningThreadMessagesActionTest {

    @Test
    void architectMessage_rendersDecisionsAsLabeledListWhenMultipleLines() {
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new FeatureRoomStateStore(), reg);
        assertEquals(
                "OK",
                init.run(
                        null,
                        Map.of("contextId", "role-arch", "channelId", "ch"),
                        Map.of("profileId", "software_feature_planning")));

        var upsert = new UpsertArtifactSectionDataAction(store, reg);
        assertEquals(
                "OK",
                upsert.run(
                        null,
                        Map.of("contextId", "role-arch"),
                        Map.of(
                                "artifactId",
                                "decision_log",
                                "sectionId",
                                "decisions",
                                "mode",
                                "replace",
                                "data",
                                Map.of("decision_text", "Use Postgres for primary store"))));
        assertEquals(
                "OK",
                upsert.run(
                        null,
                        Map.of("contextId", "role-arch"),
                        Map.of(
                                "artifactId",
                                "decision_log",
                                "sectionId",
                                "decisions",
                                "mode",
                                "append",
                                "data",
                                Map.of("decision_text", "Expose read models via cached projections"))));

        var action = new BuildRolePlanningThreadMessagesAction(store);
        @SuppressWarnings("unchecked")
        Map<String, Object> out =
                (Map<String, Object>) action.run(null, Map.of("contextId", "role-arch"), Map.of());
        String arch = (String) out.get("architectThreadMessage");
        assertTrue(arch.contains("**Decisions**"));
        assertTrue(arch.contains("1. Decision: Use Postgres for primary store"));
        assertTrue(arch.contains("2. Decision: Expose read models via cached projections"));
        assertTrue(arch.contains("Postgres"));
        assertTrue(arch.contains("cached projections"));
    }
}
