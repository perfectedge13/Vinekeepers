package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.workflow.UnresolvedItem;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.state.workflow.UnresolvedItemStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningDeliberationLedgerSyncTest {

    @Test
    void upsertSkipsNewOpenWhenMergedItemIsSemanticallySimilar() {
        String original =
                "Should we implement runtime model resolution in Bootstrap while keeping YAML as the source of truth?";
        String paraphrase =
                "Should we implement runtime model resolution in Bootstrap while keeping yaml as the source of truth";
        String fp = UnresolvedItemLedger.normalizeFingerprint(original);
        UnresolvedItem merged =
                new UnresolvedItem(
                        "uq_m1",
                        fp,
                        UnresolvedItemStatus.MERGED,
                        "",
                        original,
                        "blocking",
                        Map.of("channel", PlanningGapEvaluator.PLANNING_CLARIFICATION_CHANNEL),
                        List.of(Map.of("raw", "yes", "normalized", "yes")),
                        List.of(),
                        0);
        UnresolvedItemLedger ledger = UnresolvedItemLedger.empty().withAdded(merged);

        PlanningQuestionRankingPolicy.RankedClarification ranked =
                new PlanningQuestionRankingPolicy.RankedClarification(
                        true,
                        "",
                        "[]",
                        "{}",
                        1,
                        List.of(),
                        false,
                        paraphrase);

        PlanningDeliberationLedgerSync.UpsertResult out =
                PlanningDeliberationLedgerSync.upsertOpenQuestion(ledger, ranked);
        assertTrue(out.activeItemId().isEmpty());
    }

    @Test
    void reconcileCancelsOpenPlanningWhenNoAllowedGaps() {
        UnresolvedItem open =
                new UnresolvedItem(
                        "uq_x",
                        "fp",
                        UnresolvedItemStatus.OPEN,
                        "",
                        "Still there?",
                        "normal",
                        Map.of("channel", PlanningGapEvaluator.PLANNING_CLARIFICATION_CHANNEL, "gapId", "old_gap"),
                        List.of(),
                        List.of(),
                        0);
        UnresolvedItemLedger ledger = UnresolvedItemLedger.empty().withAdded(open);
        UnresolvedItemLedger next = PlanningDeliberationLedgerSync.reconcileCanonicalOpenGaps(ledger, java.util.Set.of());
        long openCount =
                next.items().stream()
                        .filter(i -> i.getStatus() == UnresolvedItemStatus.OPEN)
                        .count();
        assertEquals(0, openCount);
        assertTrue(
                next.items().stream()
                        .anyMatch(
                                i -> i.getId().equals("uq_x")
                                        && i.getStatus() == UnresolvedItemStatus.CANCELLED));
    }

    @Test
    void upsertCanonicalGapUsesGapIdInSource() {
        PlanningQuestionRankingPolicy.RankedClarification ranked =
                new PlanningQuestionRankingPolicy.RankedClarification(
                        true,
                        "",
                        "[]",
                        "{\"questionText\":\"Q?\",\"gapId\":\"my_gap\"}",
                        0,
                        List.of(),
                        false,
                        "Q?");
        PlanningDeliberationLedgerSync.UpsertResult out =
                PlanningDeliberationLedgerSync.upsertOpenQuestionForCanonicalGap(
                        UnresolvedItemLedger.empty(), ranked, "my_gap", false, 2, "NARROW");
        assertTrue(out.activeItemId().isPresent());
        UnresolvedItem it =
                out.ledger().items().stream()
                        .filter(i -> i.getId().equals(out.activeItemId().get()))
                        .findFirst()
                        .orElseThrow();
        assertEquals("my_gap", it.getSource().get("gapId"));
        assertEquals("2", it.getSource().get("askCount"));
        assertEquals("NARROW", it.getSource().get("escalationLevel"));
    }

    @Test
    void lastMergedQuestionTextForPlanningGapReturnsLatestForGapId() {
        UnresolvedItem older =
                new UnresolvedItem(
                        "a",
                        "f1",
                        UnresolvedItemStatus.MERGED,
                        "",
                        "First?",
                        "normal",
                        Map.of("channel", PlanningGapEvaluator.PLANNING_CLARIFICATION_CHANNEL, "gapId", "g1"),
                        List.of(),
                        List.of(),
                        0);
        UnresolvedItem newer =
                new UnresolvedItem(
                        "b",
                        "f2",
                        UnresolvedItemStatus.MERGED,
                        "",
                        "Second?",
                        "normal",
                        Map.of("channel", PlanningGapEvaluator.PLANNING_CLARIFICATION_CHANNEL, "gapId", "g1"),
                        List.of(),
                        List.of(),
                        0);
        UnresolvedItemLedger ledger = UnresolvedItemLedger.empty().withAdded(older).withAdded(newer);
        assertEquals(
                "Second?",
                PlanningDeliberationLedgerSync.lastMergedQuestionTextForPlanningGap(ledger, "g1").orElseThrow());
    }
}
