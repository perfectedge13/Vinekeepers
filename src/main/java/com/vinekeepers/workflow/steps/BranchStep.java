package com.vinekeepers.workflow.steps;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepResult;
import com.vinekeepers.workflow.WorkflowStep;

import java.util.List;
import java.util.Map;

/**
 * Step that branches to another step index based on conditions (state key truthy or "else").
 */
public final class BranchStep implements WorkflowStep {

    private final List<Map<String, Object>> branches;

    public BranchStep(List<Map<String, Object>> branches) {
        this.branches = branches != null ? List.copyOf(branches) : List.of();
    }

    @Override
    public StepResult execute(Event event, ConfigurableWorkflowState state, int stepIndex) {
        for (Map<String, Object> branch : branches) {
            Object whenObj = branch.get("when");
            Object nextObj = branch.get("next");
            if (nextObj == null) continue;
            int next = nextObj instanceof Number n ? n.intValue() : -1;
            if (next < 0) continue;
            if ("else".equals(whenObj) || (whenObj instanceof String key && isTruthy(state.get(key)))) {
                return StepResult.goTo(next);
            }
        }
        return StepResult.goTo(stepIndex + 1);
    }

    private static boolean isTruthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return !s.isBlank();
        return true;
    }
}
