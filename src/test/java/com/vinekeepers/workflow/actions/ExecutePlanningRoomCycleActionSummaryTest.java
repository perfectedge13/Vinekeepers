package com.vinekeepers.workflow.actions;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.workflow.planning.PlanningQuestionRankingPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutePlanningRoomCycleActionSummaryTest {

    @Test
    void openClarificationSummaryIncludesQuestionWhyNextAndPlainText() {
        FeaturePlanState plan = new FeaturePlanState(
                "c",
                "f",
                "s",
                "room",
                null,
                null,
                "t",
                "Add retry logic to the API client",
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
                "software_feature_planning",
                Map.of(),
                null,
                null);
        var ranked =
                PlanningQuestionRankingPolicy.rank(
                        null,
                        List.of("Should we implement exponential backoff for retries when calling the public API?"),
                        3);
        assertTrue(ranked.userInputRequired());
        String summary =
                com.vinekeepers.workflow.planning.PlanningCyclePipeline.buildOrchestratorSummary(
                        plan, true, "", ranked, 1, false, false, "");
        assertTrue(summary.contains("exponential backoff"));
        assertTrue(summary.contains("Next I'll"));
        assertTrue(summary.contains("Here's my current understanding"));
        assertTrue(summary.contains("plain text"));
        assertFalse(ranked.useStructuredChoices());
    }
}
