package com.vinekeepers.profile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime state for one section under an artifact (generic, profile-driven).
 */
public final class SectionState {

    public static final String STATUS_EMPTY = "EMPTY";
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_COMPLETE = "COMPLETE";

    private final String sectionId;
    private final String status;
    private final Map<String, Object> values;
    private final List<Map<String, Object>> entries;

    public SectionState(
            String sectionId,
            String status,
            Map<String, Object> values,
            List<Map<String, Object>> entries) {
        this.sectionId = Objects.requireNonNull(sectionId, "sectionId").trim();
        this.status = status != null && !status.isBlank() ? status.trim() : STATUS_EMPTY;
        this.values = copyStringKeyMap(values);
        this.entries = copyEntries(entries);
    }

    private static Map<String, Object> copyStringKeyMap(Map<String, Object> in) {
        if (in == null || in.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : in.entrySet()) {
            if (e.getKey() != null) {
                m.put(e.getKey(), e.getValue());
            }
        }
        return Map.copyOf(m);
    }

    private static List<Map<String, Object>> copyEntries(List<Map<String, Object>> in) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : in) {
            out.add(copyStringKeyMap(row));
        }
        return List.copyOf(out);
    }

    public String getSectionId() {
        return sectionId;
    }

    public String getStatus() {
        return status;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public List<Map<String, Object>> getEntries() {
        return entries;
    }

    public SectionState withStatus(String newStatus) {
        return new SectionState(sectionId, newStatus, values, entries);
    }

    public SectionState withValues(Map<String, Object> newValues) {
        return new SectionState(sectionId, status, newValues, entries);
    }

    public SectionState withEntries(List<Map<String, Object>> newEntries) {
        return new SectionState(sectionId, status, values, newEntries);
    }
}
