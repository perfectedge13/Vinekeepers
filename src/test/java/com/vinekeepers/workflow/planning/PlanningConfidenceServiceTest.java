package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningConfidenceServiceTest {

    @Test
    void repoEvidenceGroundingScore_nullPlan_onlyJsonBonus() {
        String json =
                "{\"localPathPresent\":true,\"workspaceStatus\":\"MATERIALIZED\",\"blockingIssues\":0}";
        double s = PlanningConfidenceService.repoEvidenceGroundingScore(null, json);
        assertEquals(0.18, s, 1e-9);
    }

    @Test
    void repoEvidenceGroundingScore_materializedWorkspace_addsBase() {
        FeaturePlanState plan =
                minimalPlan()
                        .withWorkspaceLinkage("ws", "MATERIALIZED", "/repo/work", "");
        double s = PlanningConfidenceService.repoEvidenceGroundingScore(plan, "{}");
        assertTrue(s >= 0.5);
    }

    @Test
    void cycleBreakdown_nullPlan_recordsScoresAndFlags() {
        PlanningConfidenceBreakdown b =
                PlanningConfidenceService.cycleBreakdown(
                        null,
                        0.4,
                        true,
                        false,
                        UnresolvedItemLedger.empty(),
                        null,
                        false,
                        "");
        assertEquals(0.0, b.repoEvidenceGroundingScore(), 0.0);
        assertEquals(0.4, b.clarificationConfidenceScore(), 1e-9);
        assertTrue(b.depthOk());
        assertTrue(b.structuredParseOk());
        assertEquals(0, b.structuredKnownFactCount());
        assertEquals(0, b.materialUnknownCount());
    }

    @Test
    void cycleBreakdown_userInputRequired_incrementsMaterialUnknown() {
        PlanningConfidenceBreakdown b =
                PlanningConfidenceService.cycleBreakdown(
                        null,
                        0.0,
                        false,
                        true,
                        UnresolvedItemLedger.empty(),
                        null,
                        true,
                        "");
        assertTrue(b.materialUnknownCount() >= 2);
    }

    private static FeaturePlanState minimalPlan() {
        return new FeaturePlanState(
                "c",
                "f",
                "s",
                "room",
                null,
                null,
                "t",
                "req",
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
                null,
                null,
                null,
                "software_feature_planning_v2",
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
