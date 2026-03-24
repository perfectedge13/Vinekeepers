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
    void bulletLinesAsDiscordListBuildsLabeledItems() {
        String tab =
                PlanningThreadPacketFormatter.bulletLinesAsDiscordList(
                        "Requirement", "- One thing\n- Two things");
        assertTrue(tab.contains("1. Requirement: One thing"));
        assertTrue(tab.contains("2. Requirement: Two things"));
        assertFalse(tab.contains("| # |"));
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
        String eq = PlanningArtifactTexts.unresolvedQuestionSummary(plan);
        assertTrue(eq.contains("rollback"));
        assertFalse(eq.toLowerCase().contains("open questions"));
    }

    @Test
    void bulletLinesAsDiscordListStripsNumberedPrefixes() {
        String tab =
                PlanningThreadPacketFormatter.bulletLinesAsDiscordList("Item", "1. First\n2. Second");
        assertTrue(tab.contains("1. Item: First"));
        assertTrue(tab.contains("2. Item: Second"));
    }

    @Test
    void splitForDiscordPrefersNumberedItemBoundaries() {
        String body = """
                **Issues (tracked)**
                1. Priority: Blocking | Status: Blocking | Severity: High
                   Topic: alpha-start %s alpha-end

                2. Priority: Blocking | Status: Blocking | Severity: High
                   Topic: beta-start %s beta-end

                3. Priority: Blocking | Status: Blocking | Severity: High
                   Topic: gamma-start %s gamma-end

                **Validation strategy**
                Run targeted tests.
                """.formatted("x".repeat(780), "y".repeat(780), "z".repeat(780));
        List<String> chunks = PlanningThreadPacketFormatter.splitForDiscord(body);
        assertTrue(chunks.size() > 1);
        assertTokenPairLivesInSingleChunk(chunks, "alpha-start", "alpha-end");
        assertTokenPairLivesInSingleChunk(chunks, "beta-start", "beta-end");
        assertTokenPairLivesInSingleChunk(chunks, "gamma-start", "gamma-end");
        assertTrue(chunks.get(0).startsWith("**Planning packet"));
    }

    private static void assertTokenPairLivesInSingleChunk(List<String> chunks, String start, String end) {
        boolean foundPair = false;
        for (String chunk : chunks) {
            boolean hasStart = chunk.contains(start);
            boolean hasEnd = chunk.contains(end);
            assertEquals(hasStart, hasEnd, "token pair split across chunks");
            if (hasStart) {
                foundPair = true;
            }
        }
        assertTrue(foundPair, "token pair not found");
    }
}
