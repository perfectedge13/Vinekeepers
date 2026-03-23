package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileLoader;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SynthesizePlanDraftsActionTest {

    @Test
    void workspaceNotReadyNoteUsesHumanizedStatus(@TempDir Path workspace) throws Exception {
        WorkProfileRegistry reg = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        FeaturePlanState plan =
                new FeaturePlanState(
                        "ctx-syn",
                        "f",
                        "s",
                        "room",
                        null,
                        null,
                        "t",
                        "Request",
                        "PLANNING",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        FeaturePlanState.initialSectionStatuses(),
                        null,
                        null,
                        null,
                        null,
                        "UNRESOLVED",
                        workspace.toAbsolutePath().toString(),
                        null,
                        "software_feature_planning",
                        Map.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        store.put(plan);

        var action = new SynthesizePlanDraftsAction(store, reg);
        @SuppressWarnings("unchecked")
        Map<String, Object> spread =
                (Map<String, Object>) action.run(new Event("t", "k", Map.of()), Map.of("contextId", "ctx-syn"), Map.of());
        String note = String.valueOf(spread.get("synthesizePlanDraftsNote"));
        assertTrue(note.contains("Workspace is not ready yet"));
        assertTrue(note.contains("Not resolved yet"));
    }
}
