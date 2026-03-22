package com.vinekeepers.workflow.viewmodel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Coordinator-facing keys allowed in user templates (generic UX boundary; not planning-specific).
 */
public final class UserCopyContext {

    public static final Set<String> DEFAULT_COORDINATOR_KEYS = Set.of(
            "coordinatorSummary",
            "nextQuestionPlain",
            "progressLine",
            "whatChangedSinceLastTurn",
            "approvalPromptBody",
            "resolutionExplanation");

    private UserCopyContext() {}

    /**
     * Builds a stable sub-map of state for allowlisted template rendering.
     */
    public static Map<String, Object> slice(Map<String, ?> state, Set<String> allowedKeys) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (state == null || allowedKeys == null) {
            return out;
        }
        for (String k : allowedKeys) {
            if (k == null || k.isBlank()) {
                continue;
            }
            if (state.containsKey(k)) {
                out.put(k, Objects.toString(state.get(k), ""));
            }
        }
        return out;
    }
}
