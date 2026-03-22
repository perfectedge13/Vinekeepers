package com.vinekeepers.state.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Append-only user-visible progress events with coarse deduplication (generic deliberation transparency).
 */
public final class ProgressEventLog {

    public static final String STATE_JSON_KEY = "workflowProgressEventsJson";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_EVENTS = 30;

    private final List<Map<String, Object>> events;

    public ProgressEventLog(List<Map<String, Object>> events) {
        this.events = events != null ? new ArrayList<>(events) : new ArrayList<>();
    }

    public static ProgressEventLog empty() {
        return new ProgressEventLog(List.of());
    }

    public static ProgressEventLog fromStateJson(String json) {
        if (json == null || json.isBlank()) {
            return empty();
        }
        try {
            List<Map<String, Object>> raw = JSON.readValue(json, new TypeReference<>() {});
            return new ProgressEventLog(raw);
        } catch (Exception e) {
            return empty();
        }
    }

    public String toStateJson() {
        try {
            return JSON.writeValueAsString(events);
        } catch (Exception e) {
            return "[]";
        }
    }

    public static ProgressEventLog readFrom(Map<String, Object> state) {
        if (state == null) {
            return empty();
        }
        Object raw = state.get(STATE_JSON_KEY);
        return fromStateJson(raw != null ? raw.toString() : "");
    }

    /**
     * Appends a message if its normalized fingerprint differs from the last recorded event.
     */
    public ProgressEventLog withAppendedIfChanged(String message) {
        if (message == null || message.isBlank()) {
            return this;
        }
        String norm = normalize(message);
        if (!events.isEmpty()) {
            Map<String, Object> last = events.get(events.size() - 1);
            String prev = last.get("body") != null ? normalize(last.get("body").toString()) : "";
            if (norm.equals(prev)) {
                return this;
            }
        }
        List<Map<String, Object>> next = new ArrayList<>(events);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("at", System.currentTimeMillis());
        row.put("body", message.trim());
        next.add(row);
        while (next.size() > MAX_EVENTS) {
            next.remove(0);
        }
        return new ProgressEventLog(next);
    }

    /** Latest body for templates, or empty. */
    public String latestBody() {
        if (events.isEmpty()) {
            return "";
        }
        Object b = events.get(events.size() - 1).get("body");
        return b != null ? b.toString() : "";
    }

    public void putInto(Map<String, Object> state) {
        if (state != null) {
            state.put(STATE_JSON_KEY, toStateJson());
        }
    }

    public static void mergeIntoSpread(Map<String, Object> spread, ProgressEventLog log) {
        if (spread != null && log != null) {
            spread.put(STATE_JSON_KEY, log.toStateJson());
            spread.put("userCopyProgressEventLatest", log.latestBody());
        }
    }

    private static String normalize(String s) {
        return Objects.toString(s, "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
