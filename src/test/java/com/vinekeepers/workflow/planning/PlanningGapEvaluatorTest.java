package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.workflow.UnresolvedItem;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.state.workflow.UnresolvedItemStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.vinekeepers.workflow.planning.PlanningGapEvaluator.PLANNING_CLARIFICATION_CHANNEL;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningGapEvaluatorTest {

    @Test
    void firstOpenPlanningClarification_nullLedger_empty() {
        assertTrue(PlanningGapEvaluator.firstOpenPlanningClarification(null).isEmpty());
        assertFalse(PlanningGapEvaluator.requiresUserInputForPlanningClarification(null));
    }

    @Test
    void firstOpenPlanningClarification_emptyLedger_empty() {
        assertTrue(PlanningGapEvaluator.firstOpenPlanningClarification(UnresolvedItemLedger.empty()).isEmpty());
        assertFalse(PlanningGapEvaluator.requiresUserInputForPlanningClarification(UnresolvedItemLedger.empty()));
    }

    @Test
    void firstOpenPlanningClarification_skipsNonPlanningChannel() {
        UnresolvedItem other =
                new UnresolvedItem(
                        "uq_1",
                        "fp",
                        UnresolvedItemStatus.OPEN,
                        "",
                        "Question?",
                        "normal",
                        Map.of("channel", "other_channel"),
                        List.of(),
                        List.of(),
                        0);
        UnresolvedItemLedger ledger = UnresolvedItemLedger.empty().withAdded(other);
        assertTrue(PlanningGapEvaluator.firstOpenPlanningClarification(ledger).isEmpty());
    }

    @Test
    void firstOpenPlanningClarification_returnsOpenPlanningRowWithText() {
        UnresolvedItem open =
                new UnresolvedItem(
                        "uq_1",
                        "fp",
                        UnresolvedItemStatus.OPEN,
                        "",
                        "Which scope?",
                        "normal",
                        Map.of("channel", PLANNING_CLARIFICATION_CHANNEL),
                        List.of(),
                        List.of(),
                        0);
        UnresolvedItemLedger ledger = UnresolvedItemLedger.empty().withAdded(open);
        assertTrue(PlanningGapEvaluator.firstOpenPlanningClarification(ledger).isPresent());
        assertTrue(PlanningGapEvaluator.requiresUserInputForPlanningClarification(ledger));
    }

    @Test
    void firstOpenPlanningClarification_skipsBlankQuestionText() {
        UnresolvedItem open =
                new UnresolvedItem(
                        "uq_1",
                        "fp",
                        UnresolvedItemStatus.OPEN,
                        "",
                        "   ",
                        "normal",
                        Map.of("channel", PLANNING_CLARIFICATION_CHANNEL),
                        List.of(),
                        List.of(),
                        0);
        UnresolvedItemLedger ledger = UnresolvedItemLedger.empty().withAdded(open);
        assertTrue(PlanningGapEvaluator.firstOpenPlanningClarification(ledger).isEmpty());
    }
}
