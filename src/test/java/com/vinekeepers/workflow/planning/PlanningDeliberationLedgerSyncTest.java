package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.workflow.UnresolvedItem;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.state.workflow.UnresolvedItemStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
}
