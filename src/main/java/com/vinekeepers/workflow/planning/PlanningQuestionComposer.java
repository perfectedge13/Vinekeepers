package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.CoordinatorClarificationGapRule;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;

import java.util.List;
import java.util.Optional;

/**
 * User-facing clarification wording for a selected coordinator gap. Does not choose {@code gapId} or policy.
 */
public final class PlanningQuestionComposer {

    private PlanningQuestionComposer() {}

    public static QuestionMode questionModeForPriorAskCount(int priorAskCount) {
        if (priorAskCount >= 2) {
            return QuestionMode.BOUNDED;
        }
        if (priorAskCount >= 1) {
            return QuestionMode.NARROW;
        }
        return QuestionMode.OPEN;
    }

    public static String normalizeClarificationQuestion(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.toLowerCase().replaceAll("\\s+", " ").trim();
        return t.replaceAll("[^a-z0-9?\\s]", "");
    }

    /**
     * Escalates repeated asks on the same gap (open → narrow template → bounded options) without changing {@code gapId}.
     */
    public static String composeQuestionForAskCycle(
            String templateQuestion,
            String gapId,
            CoordinatorClarificationGapRule rule,
            UnresolvedItemLedger ledger,
            int priorAskCount) {
        String template = templateQuestion != null ? templateQuestion.trim() : "";
        if (template.isBlank() || gapId == null || gapId.isBlank()) {
            return template;
        }
        Escalation escalation = escalationLevel(priorAskCount);
        if (escalation == Escalation.BOUNDED) {
            return boundedEscalationQuestion(template, rule);
        }
        if (escalation == Escalation.NARROW) {
            return narrowEscalationQuestion(rule);
        }
        Optional<String> last =
                PlanningDeliberationLedgerSync.lastMergedQuestionTextForPlanningGap(ledger, gapId);
        if (last.isEmpty()) {
            return template;
        }
        if (!normalizeClarificationQuestion(template).equals(normalizeClarificationQuestion(last.get()))) {
            return template;
        }
        return narrowEscalationQuestion(rule);
    }

    private enum Escalation {
        OPEN,
        NARROW,
        BOUNDED
    }

    private static Escalation escalationLevel(int priorAskCount) {
        if (priorAskCount >= 2) {
            return Escalation.BOUNDED;
        }
        if (priorAskCount >= 1) {
            return Escalation.NARROW;
        }
        return Escalation.OPEN;
    }

    private static String narrowEscalationQuestion(CoordinatorClarificationGapRule rule) {
        if (rule != null && rule.getNarrowEscalationTemplate() != null && !rule.getNarrowEscalationTemplate().isBlank()) {
            return rule.getNarrowEscalationTemplate().trim();
        }
        return "";
    }

    private static String boundedEscalationQuestion(String template, CoordinatorClarificationGapRule rule) {
        List<String> options =
                rule != null && rule.getResolveAnySubstring() != null
                        ? rule.getResolveAnySubstring().stream()
                                .filter(text -> text != null && !text.isBlank())
                                .map(String::trim)
                                .distinct()
                                .limit(4)
                                .toList()
                        : List.of();
        if (!options.isEmpty()) {
            return "To unblock this, reply with exactly one option: " + joinOptions(options) + ".";
        }
        String narrow = narrowEscalationQuestion(rule);
        if (narrow.isBlank()) {
            return "";
        }
        return narrow + " Keep it to one concrete answer.";
    }

    private static String joinOptions(List<String> options) {
        if (options == null || options.isEmpty()) {
            return "";
        }
        if (options.size() == 1) {
            return options.get(0);
        }
        if (options.size() == 2) {
            return options.get(0) + " or " + options.get(1);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < options.size(); i++) {
            if (i > 0) {
                sb.append(i == options.size() - 1 ? ", or " : ", ");
            }
            sb.append(options.get(i));
        }
        return sb.toString();
    }
}
