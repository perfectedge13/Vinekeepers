package com.vinekeepers.workflow.planning;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves ordered coordinator passes from bind/state YAML ({@code planningRolePassOrder}).
 */
public final class ConfigurablePassRunner {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final List<PlanningCoordinatorRole> DEFAULT_ORDER =
            List.of(PlanningCoordinatorRole.ARRIETTY);

    private ConfigurablePassRunner() {}

    /**
     * Bind/state value: JSON array of role names, or comma-separated list (e.g. {@code ARCHITECT,AUDITOR,SCRIBE}).
     * Unknown tokens are skipped; empty after parse falls back to default order.
     */
    public static List<PlanningCoordinatorRole> resolveOrder(Map<String, Object> state, Map<String, Object> bind) {
        String raw = firstNonBlank(getString(bind, "planningRolePassOrder"), getString(state, "planningRolePassOrder"));
        if (raw == null || raw.isBlank()) {
            return DEFAULT_ORDER;
        }
        raw = raw.trim();
        List<String> tokens = new ArrayList<>();
        if (raw.startsWith("[")) {
            try {
                List<String> parsed = JSON.readValue(raw, new TypeReference<>() {});
                if (parsed != null) {
                    for (String s : parsed) {
                        if (s != null && !s.isBlank()) {
                            tokens.add(s.trim());
                        }
                    }
                }
            } catch (Exception ignored) {
                tokens.clear();
            }
        } else {
            for (String part : raw.split(",")) {
                if (part != null && !part.isBlank()) {
                    tokens.add(part.trim());
                }
            }
        }
        LinkedHashMap<PlanningCoordinatorRole, Boolean> seen = new LinkedHashMap<>();
        for (String t : tokens) {
            PlanningCoordinatorRole r = parseRole(t);
            if (r != null) {
                seen.putIfAbsent(r, Boolean.TRUE);
            }
        }
        if (seen.isEmpty()) {
            return DEFAULT_ORDER;
        }
        return List.copyOf(seen.keySet());
    }

    private static PlanningCoordinatorRole parseRole(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }
        if ("ARCHITECT".equals(normalized)
                || "AUDITOR".equals(normalized)
                || "SCRIBE".equals(normalized)
                || "PLANNER".equals(normalized)) {
            return PlanningCoordinatorRole.ARRIETTY;
        }
        try {
            return PlanningCoordinatorRole.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }
}
