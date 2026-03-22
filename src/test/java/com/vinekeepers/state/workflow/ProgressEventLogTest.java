package com.vinekeepers.state.workflow;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressEventLogTest {

    @Test
    void dedupesConsecutiveIdenticalBodies() {
        ProgressEventLog log = ProgressEventLog.empty();
        log = log.withAppendedIfChanged("unique-dedupe-body-xyz");
        log = log.withAppendedIfChanged("unique-dedupe-body-xyz");
        String json = log.toStateJson();
        assertEquals(json.indexOf("unique-dedupe-body-xyz"), json.lastIndexOf("unique-dedupe-body-xyz"));
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
}
