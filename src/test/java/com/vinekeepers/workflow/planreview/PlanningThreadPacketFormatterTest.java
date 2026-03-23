package com.vinekeepers.workflow.planreview;

import com.vinekeepers.state.planning.FeaturePlanState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningThreadPacketFormatterTest {

    @Test
    void buildConciseThreadReviewBodyAfterPacketIsEmptyWhenPlanNull() {
        assertEquals("", PlanningThreadPacketFormatter.buildConciseThreadReviewBodyAfterPacket(null, "x"));
    }

    @Test
    void buildConciseThreadReviewBodyAfterPacketPointsToPriorPacketMessages() {
        FeaturePlanState plan =
                new FeaturePlanState(
                        "ctx",
                        "f",
                        "s",
                        "room",
                        null,
                        null,
                        "t",
                        "Short request text",
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
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        String body = PlanningThreadPacketFormatter.buildConciseThreadReviewBodyAfterPacket(plan, null);
        assertTrue(body.contains("**Planning packet**"));
        assertTrue(body.contains("source of truth"));
        assertFalse(body.contains("**Proposed behavior / outline**"));
    }

    @Test
    void bulletLinesAsMarkdownTableBuildsTwoColumnTable() {
        String tab =
                PlanningThreadPacketFormatter.bulletLinesAsMarkdownTable(
                        "Requirement", "- One thing\n- Two things");
        assertTrue(tab.contains("| # | Requirement |"));
        assertTrue(tab.contains("| 1 |"));
        assertTrue(tab.contains("One thing"));
        assertTrue(tab.contains("| 2 |"));
        assertTrue(tab.contains("Two things"));
    }

    @Test
    void openQuestionsSectionOmitsGenericMetaLines() {
        FeaturePlanState plan =
                new FeaturePlanState(
                        "ctx",
                        "f",
                        "s",
                        "room",
                        null,
                        null,
                        "t",
                        "Short request text",
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
                        null,
                        List.of(
                                "Are there any open questions?",
                                "What is the SLA in milliseconds for the payment callback endpoint?"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        String body = PlanningThreadPacketFormatter.buildFullPacketBody(plan, null, null);
        assertTrue(body.contains("**Open questions**"));
        assertTrue(body.contains("payment callback"));
        assertFalse(body.contains("Are there any open questions"));
    }

    @Test
    void effectiveOpenQuestionsMatchesSubstantiveUnresolvedLines() {
        FeaturePlanState plan =
                new FeaturePlanState(
                        "ctx",
                        "f",
                        "s",
                        "room",
                        null,
                        null,
                        "t",
                        "Short request text",
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
                        null,
                        List.of(
                                "List any open questions.",
                                "What is the rollback procedure if the migration fails halfway?"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        String eq = PlanningArtifactTexts.effectiveOpenQuestions(plan);
        assertTrue(eq.contains("rollback"));
        assertFalse(eq.toLowerCase().contains("open questions"));
    }

    @Test
    void bulletLinesAsMarkdownTableStripsNumberedPrefixes() {
        String tab =
                PlanningThreadPacketFormatter.bulletLinesAsMarkdownTable("Item", "1. First\n2. Second");
        assertTrue(tab.contains("First"));
        assertTrue(tab.contains("Second"));
    }
}
