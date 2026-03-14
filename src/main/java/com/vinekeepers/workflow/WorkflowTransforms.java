package com.vinekeepers.workflow;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Shared string transforms for workflow steps: branch conditions (trim, lower, upper only)
 * and capture/extract (trim, lower, upper, default). Null/blank-safe.
 */
public final class WorkflowTransforms {

    private WorkflowTransforms() {}

    /**
     * Trim; null or non-String returns as-is (for non-string state values).
     */
    public static String trim(String value) {
        if (value == null) return null;
        return value.trim();
    }

    /**
     * Lowercase (ROOT locale); null or non-String returns as-is.
     */
    public static String lower(String value) {
        if (value == null) return null;
        return value.toLowerCase(Locale.ROOT);
    }

    /**
     * Uppercase (ROOT locale); null or non-String returns as-is.
     */
    public static String upper(String value) {
        if (value == null) return null;
        return value.toUpperCase(Locale.ROOT);
    }

    /**
     * If value is null or blank, return defaultIfBlank; otherwise return value.
     * For capture/extract only; not used in branch conditions.
     */
    public static String defaultTransform(String value, String defaultIfBlank) {
        if (value == null || value.isBlank()) return defaultIfBlank != null ? defaultIfBlank : "";
        return value;
    }

    /**
     * Apply a single transform by name. Branch-allowed: trim, lower, upper.
     * Capture/extract also allow "default" but it requires a default value (handled by callers).
     */
    public static String applyOne(String value, String transformName, String defaultForDefault) {
        if (value == null && !"default".equals(transformName)) return null;
        return switch (transformName != null ? transformName.trim().toLowerCase(Locale.ROOT) : "") {
            case "trim" -> trim(value);
            case "lower" -> lower(value);
            case "upper" -> upper(value);
            case "default" -> defaultTransform(value, defaultForDefault);
            default -> value;
        };
    }

    /**
     * Apply a list of transforms in order. For branch: only trim, lower, upper are applied;
     * "default" is ignored in branch context. For capture/extract, "default" may be used
     * with a separate default value per mapping.
     *
     * @param value        current value (may be null)
     * @param transformNames list of transform names (e.g. [trim, lower])
     * @param defaultForDefault value to use when "default" transform is in the list and value is null/blank
     */
    public static String applyAll(String value, List<String> transformNames, String defaultForDefault) {
        if (transformNames == null || transformNames.isEmpty()) return value;
        String current = value;
        for (String name : transformNames) {
            if (name == null) continue;
            String t = name.trim().toLowerCase(Locale.ROOT);
            if ("default".equals(t)) {
                current = defaultTransform(current, defaultForDefault);
            } else {
                current = applyOne(current, t, null);
            }
        }
        return current;
    }

    /**
     * Coerce to string for comparison; null stays null.
     */
    public static String asString(Object value) {
        if (value == null) return null;
        return Objects.toString(value);
    }

    /**
     * Check if value is null or blank after optional trim.
     */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
