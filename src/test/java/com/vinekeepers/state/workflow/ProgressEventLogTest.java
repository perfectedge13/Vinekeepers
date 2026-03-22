package com.vinekeepers.state.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressEventLogTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void dedupesConsecutiveIdenticalBodies() throws Exception {
        ProgressEventLog log = ProgressEventLog.empty();
        log = log.withAppendedIfChanged("unique-dedupe-body-xyz");
        log = log.withAppendedIfChanged("unique-dedupe-body-xyz");
        JsonNode arr = JSON.readTree(log.toStateJson());
        assertTrue(arr.isArray());
        assertEquals(1, arr.size());
    }

    @Test
    void roundTripAndLatest() {
        ProgressEventLog log = ProgressEventLog.empty().withAppendedIfChanged("step a").withAppendedIfChanged("step b");
        String json = log.toStateJson();
        ProgressEventLog back = ProgressEventLog.fromStateJson(json);
        assertTrue(back.latestBody().contains("step b"));
        Map<String, Object> spread = new java.util.LinkedHashMap<>();
        ProgressEventLog.mergeIntoSpread(spread, back);
        assertTrue(spread.containsKey(ProgressEventLog.STATE_JSON_KEY));
        assertEquals("step b", spread.get("userCopyProgressEventLatest"));
    }

    @Test
    void typedAppendDedupesLikeLegacy() throws Exception {
        String line = "Merged clarification; reran passes.";
        ProgressEventLog log =
                ProgressEventLog.empty().withAppendedTyped("pass_status", line, "info", java.util.Map.of());
        log = log.withAppendedTyped("pass_status", line, "info", java.util.Map.of());
        JsonNode arr = JSON.readTree(log.toStateJson());
        assertTrue(arr.isArray());
        assertEquals(1, arr.size());
    }
}
