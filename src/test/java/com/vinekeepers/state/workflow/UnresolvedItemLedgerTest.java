package com.vinekeepers.state.workflow;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnresolvedItemLedgerTest {

    @Test
    void roundTripJson() {
        UnresolvedItem item =
                new UnresolvedItem(
                        "uq_1",
                        "fp1",
                        UnresolvedItemStatus.OPEN,
                        "ask",
                        "What scope?",
                        "blocking",
                        Map.of("pass", "p1"),
                        List.of(),
                        List.of("requirements_spec/narrative/feature_summary"));
        UnresolvedItemLedger ledger = UnresolvedItemLedger.empty().withAdded(item);
        String json = ledger.toStateJson();
        UnresolvedItemLedger back = UnresolvedItemLedger.fromStateJson(json);
        assertEquals(1, back.items().size());
        assertEquals("uq_1", back.items().get(0).getId());
        assertTrue(back.hasOpenItems());
    }

    @Test
    void findByFingerprint() {
        UnresolvedItem item =
                new UnresolvedItem(
                        "a",
                        UnresolvedItemLedger.normalizeFingerprint("Hello?"),
                        UnresolvedItemStatus.OPEN,
                        "",
                        "Hello?",
                        "",
                        Map.of(),
                        List.<Map<String, String>>of(),
                        List.of());
        UnresolvedItemLedger ledger = new UnresolvedItemLedger(java.util.List.of(item));
        Optional<UnresolvedItem> found =
                ledger.findByFingerprint(UnresolvedItemLedger.normalizeFingerprint("Hello?"));
        assertTrue(found.isPresent());
    }

    @Test
    void mergeIntoStateMap() {
        Map<String, Object> state = new LinkedHashMap<>();
        UnresolvedItemLedger.empty().putInto(state);
        assertTrue(state.containsKey(UnresolvedItemLedger.STATE_JSON_KEY));
    }

    @Test
    void hasOpenBlockingSeverityOnlyWhenOpenAndBlocking() {
        UnresolvedItem blocking =
                new UnresolvedItem(
                        "b",
                        "f",
                        UnresolvedItemStatus.OPEN,
                        "",
                        "q",
                        "blocking",
                        Map.of(),
                        List.<Map<String, String>>of(),
                        List.of());
        assertTrue(new UnresolvedItemLedger(List.of(blocking)).hasOpenBlockingSeverity());
        UnresolvedItem normal =
                new UnresolvedItem(
                        "n",
                        "f2",
                        UnresolvedItemStatus.OPEN,
                        "",
                        "q2",
                        "normal",
                        Map.of(),
                        List.<Map<String, String>>of(),
                        List.of());
        assertFalse(new UnresolvedItemLedger(List.of(normal)).hasOpenBlockingSeverity());
    }

    @Test
    void allResolvedNoOpen() {
        UnresolvedItem closed =
                new UnresolvedItem(
                        "x",
                        "f",
                        UnresolvedItemStatus.MERGED,
                        "",
                        "",
                        "",
                        Map.of(),
                        List.<Map<String, String>>of(),
                        List.of());
        assertFalse(new UnresolvedItemLedger(java.util.List.of(closed)).hasOpenItems());
    }

    @Test
    void mergeClosedFingerprintDetected() {
        String q = "What is the deployment target?";
        String fp = UnresolvedItemLedger.normalizeFingerprint(q);
        UnresolvedItem merged =
                new UnresolvedItem(
                        "i1",
                        fp,
                        UnresolvedItemStatus.MERGED,
                        "",
                        q,
                        "",
                        Map.of(),
                        List.of(),
                        List.of());
        UnresolvedItemLedger ledger = UnresolvedItemLedger.empty().withAdded(merged);
        assertTrue(ledger.hasFingerprintMergeClosed(q));
        assertFalse(ledger.hasFingerprintMergeClosed("totally different question"));
    }

    @Test
    void cancelledOpenPlanningExceptKeepsId() {
        UnresolvedItem open =
                new UnresolvedItem(
                        "keep",
                        "fp",
                        UnresolvedItemStatus.OPEN,
                        "",
                        "q",
                        "",
                        Map.of("channel", "planning_clarification"),
                        List.of(),
                        List.of());
        UnresolvedItemLedger led = UnresolvedItemLedger.empty().withAdded(open);
        UnresolvedItemLedger next = led.withCancelledOpenPlanningExcept("keep");
        assertEquals(UnresolvedItemStatus.OPEN, next.items().get(0).getStatus());
    }
}
