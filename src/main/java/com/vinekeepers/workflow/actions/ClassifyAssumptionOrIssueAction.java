package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeaturePlanStateStore;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Rule-based classifier: append plan issue on blocker language, assumption on uncertainty.
 */
public final class ClassifyAssumptionOrIssueAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final Pattern BLOCKER_PATTERN = Pattern.compile(
            "\\b(cannot|can't|blocked|blocker|impossible|contradiction|no access|error|fail|unavailable)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern UNCERTAIN_PATTERN = Pattern.compile(
            "\\b(assume|assuming|probably|might|maybe|unclear|unknown|tbd|guess)\\b|\\?\\s*$",
            Pattern.CASE_INSENSITIVE);

    private final FeaturePlanStateStore planStateStore;

    public ClassifyAssumptionOrIssueAction(FeaturePlanStateStore planStateStore) {
        this.planStateStore = planStateStore;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        if (planStateStore == null) {
            return "FeaturePlanStateStore not available.";
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        String text = firstNonBlank(getString(bind, "discoveryAnswerRaw"), getString(state, "discoveryAnswerRaw"));
        if (contextId == null || contextId.isBlank()) {
            return "Missing contextId for classify_assumption_or_issue.";
        }
        if (text == null || text.isBlank()) {
            return "SKIP";
        }
        String norm = text.toLowerCase(Locale.ROOT);
        boolean blocker = BLOCKER_PATTERN.matcher(norm).find();
        boolean uncertain = UNCERTAIN_PATTERN.matcher(norm).find();
        if (blocker) {
            AppendIssueAction issue = new AppendIssueAction(planStateStore);
            return issue.run(event, state, Map.of("contextId", contextId, "text", text.trim()));
        }
        if (uncertain) {
            AppendAssumptionAction asm = new AppendAssumptionAction(planStateStore);
            return asm.run(event, state, Map.of("contextId", contextId, "text", text.trim()));
        }
        return "NONE";
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }
}
