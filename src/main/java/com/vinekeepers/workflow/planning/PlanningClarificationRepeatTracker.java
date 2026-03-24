package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.PlanningCanonicalNextAction;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;

import java.util.Locale;
import java.util.Map;

/**
 * Bookkeeping for clarification repeat / stuck hints on the workflow spread. Does not choose when to ask the user; only
 * projects repeat signals from ledger + prior-question state.
 */
public final class PlanningClarificationRepeatTracker {

    private PlanningClarificationRepeatTracker() {}

    public static void apply(
            Map<String, Object> spread,
            Map<String, Object> state,
            UnresolvedItemLedger ledger,
            PlanningCanonicalNextAction nextAction) {
        int maxRepeat = ledger != null ? ledger.maxOpenItemRepeatCount() : 0;
        spread.put("planningClarificationRepeatCount", String.valueOf(maxRepeat));
        if (nextAction != PlanningCanonicalNextAction.ASK_USER) {
            spread.put("planningClarificationStuck", "false");
            spread.put("planningClarificationStuckHint", "");
            return;
        }
        String current = normalizeForRepeatCompare(PlanningProgressProjection.getString(spread, "planningClarificationQuestionText"));
        String previous = normalizeForRepeatCompare(PlanningProgressProjection.getString(state, "planningPreviousClarificationQuestionText"));
        boolean sameNonEmpty = !current.isEmpty() && current.equals(previous);
        if (sameNonEmpty && maxRepeat >= 1) {
            spread.put("planningClarificationStuck", "true");
            spread.put(
                    "planningClarificationStuckHint",
                    "The same clarification question is being asked again after your last answer; consider adding detail or confirming assumptions.");
        } else {
            spread.put("planningClarificationStuck", "false");
            spread.put("planningClarificationStuckHint", "");
        }
    }

    static String normalizeForRepeatCompare(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace("\r\n", " ").replace("\n", " ").trim().toLowerCase(Locale.ROOT);
        return s.replaceAll("\\s+", " ");
    }
}
