package com.vinekeepers.workflow.planning;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/** JSON map on {@link com.vinekeepers.state.planning.FeaturePlanState#getPlanningGapAskCountsJson()} (gapId → count). */
public final class PlanningGapAskCounts {

    private static final ObjectMapper JSON = new ObjectMapper();

    private PlanningGapAskCounts() {}

    public static int countForGap(String planningGapAskCountsJson, String gapId) {
        if (gapId == null || gapId.isBlank()) {
            return 0;
        }
        Map<String, Integer> m = parse(planningGapAskCountsJson);
        return m.getOrDefault(gapId.trim(), 0);
    }

    public static String incrementAsk(String planningGapAskCountsJson, String gapId) {
        if (gapId == null || gapId.isBlank()) {
            return planningGapAskCountsJson != null && !planningGapAskCountsJson.isBlank()
                    ? planningGapAskCountsJson
                    : "{}";
        }
        Map<String, Integer> m = new LinkedHashMap<>(parse(planningGapAskCountsJson));
        String id = gapId.trim();
        m.put(id, m.getOrDefault(id, 0) + 1);
        return write(m);
    }

    public static String incrementFailedMerge(String planningGapAskCountsJson, String gapId) {
        if (gapId == null || gapId.isBlank()) {
            return planningGapAskCountsJson != null && !planningGapAskCountsJson.isBlank()
                    ? planningGapAskCountsJson
                    : "{}";
        }
        Map<String, Integer> m = new LinkedHashMap<>(parse(planningGapAskCountsJson));
        String key = gapId.trim() + ":failedMerge";
        m.put(key, m.getOrDefault(key, 0) + 1);
        return write(m);
    }

    private static Map<String, Integer> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Integer> m = JSON.readValue(raw.trim(), new TypeReference<>() {});
            return m != null ? new LinkedHashMap<>(m) : new LinkedHashMap<>();
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static String write(Map<String, Integer> m) {
        try {
            return JSON.writeValueAsString(m);
        } catch (Exception e) {
            return "{}";
        }
    }
}
