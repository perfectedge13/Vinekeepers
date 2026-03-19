package com.vinekeepers.workflow.actions;

import com.vinekeepers.profile.TestWorkProfiles;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassifyAssumptionOrIssueActionTest {

    @Test
    void blockerText_appendsIssue() {
        var reg = TestWorkProfiles.loadFromRepoConfig();
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new com.vinekeepers.state.planning.FeatureRoomStateStore(), reg);
        init.run(null, Map.of("contextId", "c1", "channelId", "ch"), Map.of());
        var action = new ClassifyAssumptionOrIssueAction(store);
        assertEquals("OK", action.run(null,
                Map.of("contextId", "c1", "discoveryAnswerRaw", "We cannot proceed without API access."),
                Map.of()));
        assertEquals(1, store.getByContextId("c1").orElseThrow().getIssues().size());
    }

    @Test
    void uncertainText_appendsAssumption() {
        var reg = TestWorkProfiles.loadFromRepoConfig();
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        var init = new InitializeFeaturePlanStateAction(store, new com.vinekeepers.state.planning.FeatureRoomStateStore(), reg);
        init.run(null, Map.of("contextId", "c2", "channelId", "ch"), Map.of());
        var action = new ClassifyAssumptionOrIssueAction(store);
        assertEquals("OK", action.run(null,
                Map.of("contextId", "c2", "discoveryAnswerRaw", "We assume the default branch is main."),
                Map.of()));
        assertEquals(1, store.getByContextId("c2").orElseThrow().getAssumptions().size());
    }
}
