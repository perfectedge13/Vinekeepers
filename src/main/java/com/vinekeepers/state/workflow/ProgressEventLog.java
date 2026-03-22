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
 * Events may be legacy ({@code body} only) or typed ({@code kind}, {@code message}, {@code severity}, {@code refs}).
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
            String prev = normalize(eventMessage(last));
            if (norm.equals(prev)) {
                return this;
            }
        }
        List<Map<String, Object>> next = new ArrayList<>(events);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("at", System.currentTimeMillis());
        row.put("body", message.trim());
        row.put("kind", "legacy");
        row.put("message", message.trim());
        row.put("severity", "info");
        row.put("refs", Map.of());
        next.add(row);
        while (next.size() > MAX_EVENTS) {
            next.remove(0);
        }
        return new ProgressEventLog(next);
    }

    /**
     * Typed progress line; dedupes on normalized {@code message} like legacy {@link #withAppendedIfChanged}.
     */
    public ProgressEventLog withAppendedTyped(
            String kind, String message, String severity, Map<String, String> refs) {
        if (message == null || message.isBlank()) {
            return this;
        }
        String norm = normalize(message);
        if (!events.isEmpty()) {
            Map<String, Object> last = events.get(events.size() - 1);
            String prev = normalize(eventMessage(last));
            if (norm.equals(prev)) {
                return this;
            }
        }
        List<Map<String, Object>> next = new ArrayList<>(events);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("at", System.currentTimeMillis());
        row.put("kind", kind != null ? kind : "unknown");
        row.put("message", message.trim());
        row.put("body", message.trim());
        row.put("severity", severity != null ? severity : "info");
        row.put("refs", refs != null ? new LinkedHashMap<>(refs) : Map.of());
        next.add(row);
        while (next.size() > MAX_EVENTS) {
            next.remove(0);
        }
        return new ProgressEventLog(next);
    }

    /** Latest human-facing line (typed {@code message} or legacy {@code body}). */
    public String latestMessage() {
        if (events.isEmpty()) {
            return "";
        }
        return eventMessage(events.get(events.size() - 1));
    }

    /** @deprecated prefer {@link #latestMessage()} */
    @Deprecated
    public String latestBody() {
        return latestMessage();
    }

    public void putInto(Map<String, Object> state) {
        if (state != null) {
            state.put(STATE_JSON_KEY, toStateJson());
        }
    }

    public static void mergeIntoSpread(Map<String, Object> spread, ProgressEventLog log) {
        if (spread != null && log != null) {
            spread.put(STATE_JSON_KEY, log.toStateJson());
            spread.put("userCopyProgressEventLatest", log.latestMessage());
        }
    }

    private static String eventMessage(Map<String, Object> row) {
        if (row == null) {
            return "";
        }
        Object m = row.get("message");
        if (m != null && !m.toString().isBlank()) {
            return m.toString();
        }
        Object b = row.get("body");
        return b != null ? b.toString() : "";
    }

    private static String normalize(String s) {
        return Objects.toString(s, "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
