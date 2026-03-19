package com.vinekeepers.profile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves {@code {{stateKey}}} placeholders in strings; recurses into maps and lists.
 * Lookup is typically merged workflow state + action bind map.
 */
public final class BindPlaceholderResolver {

    private BindPlaceholderResolver() {}

    @SuppressWarnings("unchecked")
    public static Object resolveDeep(Object value, Map<String, Object> lookup) {
        if (value == null || lookup == null) {
            return value;
        }
        if (value instanceof String text) {
            return resolveString(text, lookup);
        }
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null) {
                    out.put(String.valueOf(e.getKey()), resolveDeep(e.getValue(), lookup));
                }
            }
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object o : list) {
                out.add(resolveDeep(o, lookup));
            }
            return out;
        }
        return value;
    }

    private static Object resolveString(String text, Map<String, Object> lookup) {
        String t = text.trim();
        if (t.startsWith("{{") && t.endsWith("}}")) {
            String key = t.substring(2, t.length() - 2).trim();
            return lookup.get(key);
        }
        return text;
    }

    public static Map<String, Object> asStringKeyMap(Object data) {
        if (data == null) {
            return Map.of();
        }
        if (!(data instanceof Map<?, ?> m)) {
            return Map.of();
        }
        return asStringKeyMap(m);
    }

    public static Map<String, Object> asStringKeyMap(Map<?, ?> m) {
        if (m == null || m.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() != null) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return out;
    }
}
