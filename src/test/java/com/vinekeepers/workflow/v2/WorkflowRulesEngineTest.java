package com.vinekeepers.workflow.v2;

import com.vinekeepers.state.workflow.UnresolvedItem;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.state.workflow.UnresolvedItemStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowRulesEngineTest {

    @Test
    void truthyKeyMatches() {
        Map<String, Object> state = Map.of("flag", "true");
        List<Map<String, Object>> rules =
                List.of(
                        Map.of(
                                "when",
                                Map.of("truthy", Map.of("key", "flag")),
                                "then",
                                List.of(Map.of("transition", "next_phase"))));
        Optional<String> tr = WorkflowRulesEngine.firstMatchingTransition(state, rules);
        assertTrue(tr.isPresent());
        assertEquals("next_phase", tr.get());
    }

    @Test
    void hasOpenUnresolvedUsesLedger() {
        Map<String, Object> state = new LinkedHashMap<>();
        UnresolvedItemLedger ledger =
                UnresolvedItemLedger.empty()
                        .withAdded(
                                new UnresolvedItem(
                                        "i",
                                        "f",
                                        UnresolvedItemStatus.OPEN,
                                        "",
                                        "q",
                                        "",
                                        Map.of(),
                                        List.of(),
                                        List.of()));
        ledger.putInto(state);
        List<Map<String, Object>> rules =
                List.of(
                        Map.of(
                                "when",
                                Map.of("hasOpenUnresolved", true),
                                "then",
                                List.of(Map.of("transition", "clarify"))));
        assertEquals("clarify", WorkflowRulesEngine.firstMatchingTransition(state, rules).orElseThrow());
    }

    @Test
    void ledgerMaxOpenRepeatGteMatches() {
        Map<String, Object> state = new LinkedHashMap<>();
        UnresolvedItemLedger ledger =
                UnresolvedItemLedger.empty()
                        .withAdded(
                                new UnresolvedItem(
                                        "i",
                                        "f",
                                        UnresolvedItemStatus.OPEN,
                                        "",
                                        "q",
                                        "",
                                        Map.of(),
                                        List.of(),
                                        List.of(),
                                        3));
        ledger.putInto(state);
        List<Map<String, Object>> rules =
                List.of(
                        Map.of(
                                "when",
                                Map.of("ledgerMaxOpenRepeatGte", Map.of("gte", 2)),
                                "then",
                                List.of(Map.of("transition", "escalate"))));
        assertEquals("escalate", WorkflowRulesEngine.firstMatchingTransition(state, rules).orElseThrow());
    }
}
