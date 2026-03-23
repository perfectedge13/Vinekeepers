package com.vinekeepers.state.planning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanningFailureCategoryTest {

    @Test
    void parse_blankIsNone() {
        assertEquals(PlanningFailureCategory.NONE, PlanningFailureCategory.parse(null));
        assertEquals(PlanningFailureCategory.NONE, PlanningFailureCategory.parse("  "));
    }

    @Test
    void parse_knownNameIsCaseInsensitive() {
        assertEquals(
                PlanningFailureCategory.SYNTHESIS_REPAIR_EXHAUSTED,
                PlanningFailureCategory.parse("synthesis_repair_exhausted"));
    }

    @Test
    void parse_unknownReturnsNone() {
        assertEquals(PlanningFailureCategory.NONE, PlanningFailureCategory.parse("not_a_category"));
    }

    @Test
    void wireName_noneIsEmpty() {
        assertEquals("", PlanningFailureCategory.NONE.wireName());
        assertEquals("SYNTHESIS_JSON_INVALID", PlanningFailureCategory.SYNTHESIS_JSON_INVALID.wireName());
    }
}
