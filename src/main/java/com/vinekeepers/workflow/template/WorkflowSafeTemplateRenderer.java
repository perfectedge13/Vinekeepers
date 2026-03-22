package com.vinekeepers.workflow.template;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Renders {@code {{key}}} placeholders using only allowlisted keys (avoids leaking raw workflow state).
 */
public final class WorkflowSafeTemplateRenderer {

    private WorkflowSafeTemplateRenderer() {}

    public static String render(String template, Set<String> allowedKeys, Map<String, ?> values) {
        if (template == null) {
            return "";
        }
        Set<String> allow = allowedKeys != null ? allowedKeys : Collections.emptySet();
        Map<String, ?> v = values != null ? values : Map.of();
        String result = template;
        for (String key : new HashSet<>(allow)) {
            if (key == null || key.isBlank()) {
                continue;
            }
            Object raw = v.get(key);
            String replacement = raw != null ? raw.toString() : "";
            result = result.replace("{{" + key + "}}", replacement);
        }
        return result;
    }
}
