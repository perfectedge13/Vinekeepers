package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpreadPlanWorkspaceSignalsActionTest {

    @Test
    void nullStore_returnsSafeDefaults() {
        var action = new SpreadPlanWorkspaceSignalsAction(null);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(
                new Event("s", "k", Map.of()), Map.of("contextId", "x"), Map.of());
        assertEquals("false", out.get("repoWorkspaceReady"));
        assertEquals("false", out.get("repoLocalPathPresent"));
        assertEquals("No plan context.", out.get("repoWorkspaceStatusSummary"));
    }

    @Test
    void missingContextId_explainsInSummary() {
        var action = new SpreadPlanWorkspaceSignalsAction(new FeaturePlanStateStore());
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(
                new Event("s", "k", Map.of()), Map.of(), Map.of());
        assertEquals("Missing contextId.", out.get("repoWorkspaceStatusSummary"));
    }

    @Test
    void materializedWorkspace_emitsRepoEvidenceJson() {
        var store = new FeaturePlanStateStore();
        store.put(basePlan("ctx-r", "MATERIALIZED", "/work/r", "ssh ok"));
        var action = new SpreadPlanWorkspaceSignalsAction(store);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(
                new Event("s", "k", Map.of()), Map.of("contextId", "ctx-r"), Map.of());
        assertEquals("true", out.get("repoWorkspaceReady"));
        assertEquals("true", out.get("repoLocalPathPresent"));
        String summary = String.valueOf(out.get("repoWorkspaceStatusSummary"));
        assertTrue(summary.contains("MATERIALIZED"));
        assertTrue(summary.contains("local path recorded"));
        assertTrue(summary.contains("ssh ok"));
        String json = String.valueOf(out.get("planningRepoEvidenceJson"));
        assertTrue(json.contains("\"contextId\":\"ctx-r\""));
        assertTrue(json.contains("\"workspaceStatus\":\"MATERIALIZED\""));
        assertTrue(json.contains("\"localPathPresent\":true"));
        assertTrue(json.contains("\"accessNotes\":\"ssh ok\""));
        assertTrue(json.contains("\"planningIntakeStage\":\"GATHERING_CONTEXT\""));
    }

    @Test
    void resolvedLocalCountsAsWorkspaceReady() {
        var store = new FeaturePlanStateStore();
        store.put(basePlan("ctx-l", "RESOLVED_LOCAL", "", ""));
        var action = new SpreadPlanWorkspaceSignalsAction(store);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(
                new Event("s", "k", Map.of()), Map.of(), Map.of("contextId", "ctx-l"));
        assertEquals("true", out.get("repoWorkspaceReady"));
    }

    private static FeaturePlanState basePlan(String contextId, String workspaceStatus, String localPath, String notes) {
        return new FeaturePlanState(
                contextId,
                "f1",
                "feat-slug",
                "room-ch",
                null,
                "https://github.com/o/r",
                "title",
                null,
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
                "ws-9",
                workspaceStatus,
                localPath,
                notes,
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
    }
}
