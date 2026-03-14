package com.vinekeepers.workflow;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Evaluates a single branch condition: (stateValue, operator, compareValue, transformList).
 * Transforms apply only to the state value (trim, lower, upper). Regex is full-string match.
 */
public final class WorkflowConditionEvaluator {

    private WorkflowConditionEvaluator() {}

    /**
     * Evaluate condition. stateValue is the value from workflow state; it may be transformed
     * by transformList (trim, lower, upper only). compareValue is the RHS from config (not transformed).
     *
     * @param stateValue   value from state (may be null)
     * @param operator     equals, not_equals, blank, nonblank, contains, starts_with, regex, one_of
     * @param compareValue string or, for one_of, list of strings (YAML list); single string tolerated as one-element
     * @param transformNames optional list of transform names (only trim, lower, upper applied)
     * @return true if the condition matches
     */
    public static boolean evaluate(Object stateValue, String operator, Object compareValue, List<String> transformNames) {
        String stateStr = WorkflowTransforms.asString(stateValue);
        if (transformNames != null && !transformNames.isEmpty()) {
            for (String name : transformNames) {
                if (name == null) continue;
                String t = name.trim().toLowerCase(Locale.ROOT);
                if ("trim".equals(t) || "lower".equals(t) || "upper".equals(t)) {
                    stateStr = WorkflowTransforms.applyOne(stateStr, t, null);
                }
            }
        }
        String op = operator != null ? operator.trim().toLowerCase(Locale.ROOT) : "equals";
        return switch (op) {
            case "not_equals" -> !Objects.equals(stateStr, asString(compareValue));
            case "blank" -> WorkflowTransforms.isBlank(stateStr);
            case "nonblank" -> !WorkflowTransforms.isBlank(stateStr);
            case "contains" -> stateStr != null && stateStr.contains(asString(compareValue) != null ? asString(compareValue) : "");
            case "starts_with" -> stateStr != null && asString(compareValue) != null && stateStr.startsWith(asString(compareValue));
            case "regex" -> matchesFullString(stateStr, compareValue);
            case "one_of" -> oneOf(stateStr, compareValue);
            default -> Objects.equals(stateStr, asString(compareValue));
        };
    }

    private static String asString(Object v) {
        if (v == null) return null;
        return Objects.toString(v);
    }

    private static boolean matchesFullString(String stateStr, Object patternObj) {
        String pattern = asString(patternObj);
        if (pattern == null || pattern.isBlank()) return stateStr == null || stateStr.isEmpty();
        if (stateStr == null) return false;
        try {
            Pattern p = Pattern.compile(pattern);
            return p.matcher(stateStr).matches();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private static boolean oneOf(String stateStr, Object compareValue) {
        List<String> list;
        if (compareValue instanceof List<?> l) {
            list = l.stream()
                    .filter(Objects::nonNull)
                    .map(Objects::toString)
                    .toList();
        } else if (compareValue != null) {
            list = List.of(compareValue.toString());
        } else {
            list = List.of();
        }
        if (list.isEmpty()) return stateStr == null || stateStr.isEmpty();
        String s = stateStr;
        for (String candidate : list) {
            if (Objects.equals(s, candidate)) return true;
        }
        return false;
    }
}
