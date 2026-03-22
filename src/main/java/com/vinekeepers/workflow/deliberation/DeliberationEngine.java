package com.vinekeepers.workflow.deliberation;

import java.util.Map;

/**
 * Derives generic {@code deliberationPhase} from legacy planning spread keys (bridge until full YAML phase graphs own state).
 */
public final class DeliberationEngine {

    private DeliberationEngine() {}

    /**
     * Maps {@code planningPhase} to a workflow-agnostic phase id for rules and templates.
     */
    public static void applyDerivedDeliberationSpread(Map<String, Object> spread) {
        if (spread == null) {
            return;
        }
        String pp = string(spread.get("planningPhase"));
        String phase =
                switch (pp) {
                    case "WAITING_FOR_CLARIFICATION" -> "await_user";
                    case "READY_FOR_REVIEW" -> "review_ready";
                    case "REVISING" -> "autonomous_draft";
                    case "REQUEST_EXPANSION" -> "autonomous_draft";
                    case "DRAFTING" -> "autonomous_draft";
                    case "CRITIQUING" -> "autonomous_draft";
                    default -> pp.isBlank() ? "intake" : "other";
                };
        spread.put("deliberationPhase", phase);
    }

    private static String string(Object o) {
        return o != null ? o.toString().trim() : "";
    }
}
