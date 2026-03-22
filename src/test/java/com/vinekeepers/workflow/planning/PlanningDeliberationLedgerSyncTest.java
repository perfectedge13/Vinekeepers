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
    void upsertThenMergeClosesItem() {
        var ranked =
                new PlanningQuestionRankingPolicy.RankedClarification(
                        true,
                        "",
                        "[]",
                        "{\"questionText\":\"What is the API base URL?\"}",
                        1,
                        List.of(),
                        false,
                        "What is the API base URL?");
        var up = PlanningDeliberationLedgerSync.upsertOpenQuestion(UnresolvedItemLedger.empty(), ranked);
        assertTrue(up.activeItemId().isPresent());
        UnresolvedItemLedger after = up.ledger();
        assertTrue(after.hasOpenItems());

        UnresolvedItemLedger merged =
                PlanningDeliberationLedgerSync.mergeAnswerIntoLedger(
                        after, up.activeItemId().get(), "What is the API base URL?", "https://api.example", "https://api.example");
        assertTrue(merged.findByFingerprint(UnresolvedItemLedger.normalizeFingerprint("What is the API base URL?"))
                .isPresent());
        UnresolvedItem it =
                merged.findByFingerprint(UnresolvedItemLedger.normalizeFingerprint("What is the API base URL?"))
                        .orElseThrow();
        assertEquals(UnresolvedItemStatus.MERGED, it.getStatus());
        assertEquals(1, it.getAnswers().size());
    }

    @Test
    void supersedeOpenPlanningQuestion() {
        var r1 =
                new PlanningQuestionRankingPolicy.RankedClarification(
                        true, "", "[]", "{\"questionText\":\"Q1?\"}", 0, List.of(), false, "Q1?");
        var u1 = PlanningDeliberationLedgerSync.upsertOpenQuestion(UnresolvedItemLedger.empty(), r1);
        String id1 = u1.activeItemId().orElseThrow();

        var r2 =
                new PlanningQuestionRankingPolicy.RankedClarification(
                        true, "", "[]", "{\"questionText\":\"Q2?\"}", 0, List.of(), false, "Q2?");
        var u2 = PlanningDeliberationLedgerSync.upsertOpenQuestion(u1.ledger(), r2);
        assertTrue(u2.activeItemId().isPresent());
        UnresolvedItemLedger led = u2.ledger();
        UnresolvedItem first = led.items().stream().filter(i -> i.getId().equals(id1)).findFirst().orElseThrow();
        assertEquals(UnresolvedItemStatus.CANCELLED, first.getStatus());
    }
}
