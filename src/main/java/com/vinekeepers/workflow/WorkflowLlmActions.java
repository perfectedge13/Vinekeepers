package com.vinekeepers.workflow;

import java.util.Set;

/**
 * Actions that may receive merged workflow / step {@code llm} settings via {@link com.vinekeepers.workflow.steps.CallActionStep}.
 */
public final class WorkflowLlmActions {

    private static final Set<String> IDS = Set.of(
            "run_llm_planning_synthesis",
            "run_request_expansion_llm");

    private WorkflowLlmActions() {}

    public static boolean isLlmCapable(String actionId) {
        return actionId != null && IDS.contains(actionId);
    }
}
