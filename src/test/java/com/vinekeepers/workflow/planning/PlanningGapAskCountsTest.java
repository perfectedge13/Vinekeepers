package com.vinekeepers.workflow.planning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanningGapAskCountsTest {

    @Test
    void countForGap_blankGapId_returnsZero() {
        assertEquals(0, PlanningGapAskCounts.countForGap("{\"g1\":2}", null));
        assertEquals(0, PlanningGapAskCounts.countForGap("{\"g1\":2}", "   "));
    }

    @Test
    void incrementAsk_accumulatesPerGap() {
        String j1 = PlanningGapAskCounts.incrementAsk(null, "g1");
        assertEquals(1, PlanningGapAskCounts.countForGap(j1, "g1"));
        String j2 = PlanningGapAskCounts.incrementAsk(j1, "g1");
        assertEquals(2, PlanningGapAskCounts.countForGap(j2, "g1"));
        assertEquals(0, PlanningGapAskCounts.countForGap(j2, "g2"));
    }

    @Test
    void incrementAsk_blankGapId_preservesOrDefaultsJson() {
        assertEquals("{}", PlanningGapAskCounts.incrementAsk(null, null));
        assertEquals("{\"g1\":1}", PlanningGapAskCounts.incrementAsk("{\"g1\":1}", ""));
    }

    @Test
    void incrementFailedMerge_usesDistinctKey() {
        String j = PlanningGapAskCounts.incrementFailedMerge(null, "g1");
        assertEquals(1, PlanningGapAskCounts.countForGap(j, "g1:failedMerge"));
        assertEquals(0, PlanningGapAskCounts.countForGap(j, "g1"));
    }

    @Test
    void parse_invalidJson_treatsAsEmpty() {
        assertEquals(0, PlanningGapAskCounts.countForGap("not-json", "g1"));
        String j = PlanningGapAskCounts.incrementAsk("not-json", "g1");
        assertEquals(1, PlanningGapAskCounts.countForGap(j, "g1"));
    }
}
