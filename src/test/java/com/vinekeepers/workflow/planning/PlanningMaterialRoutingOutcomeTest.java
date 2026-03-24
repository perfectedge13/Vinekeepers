package com.vinekeepers.workflow.planning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningMaterialRoutingOutcomeTest {

    @Test
    void fromLegacyResult_null_mapsToFailedBlock() {
        PlanningMaterialRoutingOutcome o = PlanningMaterialRoutingOutcome.fromLegacyResult(null);
        assertNull(o.action());
        assertEquals(PlanningPostDraftGovernor.LoopOutcome.BLOCK, o.outcome());
        assertEquals("FAILED", o.planningPhase());
        assertFalse(o.packetPostingAllowed());
    }

    @Test
    void packetPostingAllowed_trueForPostAndAssume() {
        assertTrue(
                new PlanningMaterialRoutingOutcome(
                                PlanningPostDraftAction.POST_PACKET,
                                PlanningPostDraftGovernor.LoopOutcome.POST,
                                "",
                                false,
                                false,
                                true,
                                "READY",
                                false)
                        .packetPostingAllowed());
        assertTrue(
                new PlanningMaterialRoutingOutcome(
                                PlanningPostDraftAction.ASSUME_AND_CONTINUE,
                                PlanningPostDraftGovernor.LoopOutcome.ASSUME,
                                "",
                                false,
                                false,
                                false,
                                "READY",
                                false)
                        .packetPostingAllowed());
    }

    @Test
    void packetPostingAllowed_falseForAsk() {
        assertFalse(
                new PlanningMaterialRoutingOutcome(
                                PlanningPostDraftAction.ASK_ONE_QUESTION,
                                PlanningPostDraftGovernor.LoopOutcome.ASK,
                                "",
                                true,
                                true,
                                false,
                                "WAITING",
                                false)
                        .packetPostingAllowed());
    }

    @Test
    void toLegacyResult_roundTripsFields() {
        PlanningPostDraftGovernor.Result legacy =
                new PlanningPostDraftGovernor.Result(
                        PlanningPostDraftAction.AUTONOMOUS_REDRAFT,
                        PlanningPostDraftGovernor.LoopOutcome.REDRAFT,
                        "n",
                        true,
                        false,
                        false,
                        "DRAFTING",
                        true);
        PlanningMaterialRoutingOutcome o = PlanningMaterialRoutingOutcome.fromLegacyResult(legacy);
        PlanningPostDraftGovernor.Result back = o.toLegacyResult();
        assertEquals(legacy.action(), back.action());
        assertEquals(legacy.outcome(), back.outcome());
        assertEquals(legacy.noticeMarkdown(), back.noticeMarkdown());
        assertEquals(legacy.forceUserInputRequired(), back.forceUserInputRequired());
        assertEquals(legacy.userInputRequired(), back.userInputRequired());
        assertEquals(legacy.readyToPostPacket(), back.readyToPostPacket());
        assertEquals(legacy.planningPhase(), back.planningPhase());
        assertEquals(legacy.revisionNeeded(), back.revisionNeeded());
    }
}
