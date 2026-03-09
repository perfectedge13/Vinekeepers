package com.vinekeepers.workflow.steps;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepResult;
import com.vinekeepers.workflow.WorkflowStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
            List<String> clearKeys = parseClearKeys(branch.get("clear"));
            if (whenObj instanceof Map<?, ?> whenMap) {
                String key = (String) whenMap.get("key");
                Object expectVal = whenMap.get("value");
                if (key != null && Objects.equals(String.valueOf(state.get(key)), expectVal != null ? expectVal.toString() : null)) {
                    return clearKeys.isEmpty() ? StepResult.goTo(next) : StepResult.goTo(next, clearKeys);
                }
            } else if ("else".equals(whenObj) || (whenObj instanceof String key && isTruthy(state.get(key)))) {
                return clearKeys.isEmpty() ? StepResult.goTo(next) : StepResult.goTo(next, clearKeys);
            }
        }
        return StepResult.goTo(stepIndex + 1);
    }

    private static List<String> parseClearKeys(Object clearObj) {
        if (clearObj == null) return List.of();
        if (clearObj instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object e : list) {
                if (e != null) out.add(e.toString());
            }
            return out;
        }
        return List.of();
    }

    private static boolean isTruthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return !s.isBlank();
        return true;
    }
}
