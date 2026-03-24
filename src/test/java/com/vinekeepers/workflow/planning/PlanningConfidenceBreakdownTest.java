package com.vinekeepers.workflow.planning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningConfidenceBreakdownTest {

    @Test
    void toJson_and_fromJson_roundTrip() {
        PlanningConfidenceBreakdown b =
                new PlanningConfidenceBreakdown(0.42, 0.55, true, false, 7, 3, "note");
        PlanningConfidenceBreakdown r = PlanningConfidenceBreakdown.fromJson(b.toJson());
        assertEquals(b.repoEvidenceGroundingScore(), r.repoEvidenceGroundingScore(), 1e-9);
        assertEquals(b.clarificationConfidenceScore(), r.clarificationConfidenceScore(), 1e-9);
        assertTrue(r.depthOk());
        assertFalse(r.structuredParseOk());
        assertEquals(7, r.structuredKnownFactCount());
        assertEquals(3, r.materialUnknownCount());
        assertEquals("note", r.notes());
    }

    @Test
    void fromJson_blank_returnsZeros() {
        PlanningConfidenceBreakdown r = PlanningConfidenceBreakdown.fromJson("");
        assertEquals(0.0, r.repoEvidenceGroundingScore(), 0.0);
        assertEquals(0.0, r.clarificationConfidenceScore(), 0.0);
        assertFalse(r.depthOk());
        assertFalse(r.structuredParseOk());
        assertEquals(0, r.structuredKnownFactCount());
        assertEquals(0, r.materialUnknownCount());
        assertEquals("", r.notes());
    }
}
