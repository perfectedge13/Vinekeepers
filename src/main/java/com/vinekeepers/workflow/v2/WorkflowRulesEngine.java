package com.vinekeepers.workflow.v2;

import com.vinekeepers.state.workflow.UnresolvedItemLedger;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal ordered rule evaluation for workflow v2 convergence (generic predicates and transition effects).
 * All keys under {@code when} are AND-ed.
 */
public final class WorkflowRulesEngine {

    private WorkflowRulesEngine() {}

    /**
     * @return target phase id from first matching rule's {@code transition} effect, else empty.
     */
    public static Optional<String> firstMatchingTransition(
            Map<String, Object> state, List<Map<String, Object>> rules) {
        if (rules == null || rules.isEmpty()) {
            return Optional.empty();
        }
        for (Map<String, Object> rule : rules) {
            if (rule == null) {
                continue;
            }
            Object whenObj = rule.get("when");
            if (!(whenObj instanceof Map<?, ?> when)) {
                continue;
            }
            if (when.isEmpty() || !matchesWhen(state, when)) {
                continue;
            }
            Object thenObj = rule.get("then");
            if (thenObj instanceof List<?> thenList) {
                for (Object eff : thenList) {
                    if (eff instanceof Map<?, ?> em) {
                        Object tr = em.get("transition");
                        if (tr != null && !tr.toString().isBlank()) {
                            return Optional.of(tr.toString().trim());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean matchesWhen(Map<String, Object> state, Map<?, ?> when) {
        for (Map.Entry<?, ?> e : when.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            if (!checkPredicate(state, e.getKey().toString(), e.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean checkPredicate(Map<String, Object> state, String key, Object value) {
        return switch (key) {
            case "truthy" -> {
                if (value instanceof Map<?, ?> tm) {
                    Object k = tm.get("key");
                    yield k != null && truthy(getString(state, k.toString()));
                }
                yield false;
            }
            case "equals" -> {
                if (value instanceof Map<?, ?> em) {
                    Object k = em.get("key");
                    Object v = em.get("value");
                    if (k != null) {
                        String sv = getString(state, k.toString());
                        String expect = v != null ? v.toString() : "";
                        yield expect.equals(sv != null ? sv : "");
                    }
                }
                yield false;
            }
            case "hasOpenUnresolved" -> Boolean.TRUE.equals(value)
                    && UnresolvedItemLedger.readFrom(state).hasOpenItems();
            case "allUnresolvedClosed" -> Boolean.TRUE.equals(value)
                    && !UnresolvedItemLedger.readFrom(state).hasOpenItems();
            case "sameFingerprintRepeated" -> {
                if (value instanceof Map<?, ?> m) {
                    Object gte = m.get("gte");
                    int need = parseInt(gte, 1);
                    Object fpKey = m.get("fingerprintStateKey");
                    Object cntKey = m.get("countStateKey");
                    if (fpKey != null && cntKey != null) {
                        String fp = getString(state, fpKey.toString());
                        int cnt = parseInt(getString(state, cntKey.toString()), 0);
                        yield fp != null && !fp.isBlank() && cnt >= need;
                    }
                }
                yield false;
            }
            case "ledgerMaxOpenRepeatGte" -> {
                if (value instanceof Map<?, ?> m) {
                    int need = parseInt(m.get("gte"), 1);
                    yield UnresolvedItemLedger.readFrom(state).maxOpenItemRepeatCount() >= need;
                }
                yield false;
            }
            default -> false;
        };
    }

    private static boolean truthy(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim().toLowerCase(Locale.ROOT);
        return "true".equals(t) || "1".equals(t) || "yes".equals(t);
    }

    private static String getString(Map<String, Object> state, String key) {
        if (state == null || key == null) {
            return null;
        }
        Object v = state.get(key);
        return v != null ? v.toString() : null;
    }

    private static int parseInt(Object o, int dflt) {
        if (o == null) {
            return dflt;
        }
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }
}
