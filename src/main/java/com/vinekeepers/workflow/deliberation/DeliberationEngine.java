package com.vinekeepers.workflow.deliberation;

import com.vinekeepers.workflow.planning.PlanningCanonicalDecisionSupport;

import java.util.Locale;
import java.util.Map;

/**
 * Derives generic {@code deliberationPhase} from canonical planning spread keys ({@link PlanningCanonicalDecisionSupport}).
 */
public final class DeliberationEngine {

    private DeliberationEngine() {}

    /**
     * Maps persisted canonical next action / intake stage to a workflow-agnostic phase id for rules and templates.
     */
    public static void applyDerivedDeliberationSpread(Map<String, Object> spread) {
        if (spread == null) {
            return;
        }
        String next = string(spread.get(PlanningCanonicalDecisionSupport.CANONICAL_NEXT_ACTION_KEY));
        String stage = string(spread.get(PlanningCanonicalDecisionSupport.CANONICAL_STAGE_KEY));
        String phase =
                switch (next.toUpperCase(Locale.ROOT)) {
                    case "ASK_USER" -> "await_user";
                    case "READY_FOR_PACKET" -> "review_ready";
                    case "CONTINUE_SYNTHESIS" -> "autonomous_draft";
                    case "BLOCK" -> "other";
                    default -> phaseFromStageOnly(stage);
                };
        spread.put("deliberationPhase", phase);
    }

    private static String phaseFromStageOnly(String stage) {
        if (stage == null || stage.isBlank()) {
            return "intake";
        }
        return switch (stage.toUpperCase(Locale.ROOT)) {
            case "DRAFTING", "GATHERING_CONTEXT" -> "autonomous_draft";
            case "CLARIFYING" -> "await_user";
            case "READINESS_GATE", "CRITIQUING" -> "review_ready";
            case "AWAITING_APPROVAL" -> "review_ready";
            case "FAILED" -> "other";
            default -> "other";
        };
    }

    private static String string(Object o) {
        return o != null ? o.toString().trim() : "";
    }
}
