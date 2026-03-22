package com.vinekeepers.workflow.template;

import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.viewmodel.UserCopyContext;

import java.util.Map;

/** Interpolates {@code {{key}}} using either full state or allowlisted coordinator keys. */
public final class WorkflowTemplateInterpolator {

    private WorkflowTemplateInterpolator() {}

    public static String interpolate(String template, ConfigurableWorkflowState state, WorkflowTemplatePolicy policy) {
        if (template == null) {
            return "";
        }
        WorkflowTemplatePolicy p = policy != null ? policy : WorkflowTemplatePolicy.LEGACY_FULL_STATE;
        if (p.isExposeInternal()) {
            return legacyInterpolateState(template, state);
        }
        Map<String, ?> data = state != null ? state.getData() : null;
        return interpolateMap(template, data, p);
    }

    public static String interpolate(String template, Map<String, Object> values, WorkflowTemplatePolicy policy) {
        if (template == null) {
            return "";
        }
        WorkflowTemplatePolicy p = policy != null ? policy : WorkflowTemplatePolicy.LEGACY_FULL_STATE;
        if (p.isExposeInternal()) {
            return legacyInterpolateMap(template, values);
        }
        return interpolateMap(template, values, p);
    }

    private static String interpolateMap(String template, Map<String, ?> data, WorkflowTemplatePolicy policy) {
        Map<String, ?> src = data != null ? data : Map.of();
        java.util.Set<String> allow = policy.effectiveAllowedKeys();
        Map<String, Object> slice = UserCopyContext.slice(src, allow);
        return WorkflowSafeTemplateRenderer.render(template, allow, slice);
    }

    private static String legacyInterpolateState(String template, ConfigurableWorkflowState state) {
        if (template.isBlank() || state == null || state.getData() == null) {
            return template;
        }
        String result = template;
        for (String key : state.getData().keySet()) {
            Object v = state.get(key);
            String placeholder = "{{" + key + "}}";
            if (result.contains(placeholder)) {
                result = result.replace(placeholder, v != null ? v.toString() : "");
            }
        }
        return result;
    }

    private static String legacyInterpolateMap(String template, Map<String, Object> map) {
        if (template.isBlank() || map == null || map.isEmpty()) {
            return template != null ? template : "";
        }
        String out = template;
        for (String key : map.keySet()) {
            if (key == null) {
                continue;
            }
            String placeholder = "{{" + key + "}}";
            if (out.contains(placeholder)) {
                Object v = map.get(key);
                out = out.replace(placeholder, v != null ? v.toString() : "");
            }
        }
        return out;
    }
}
