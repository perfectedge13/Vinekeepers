package com.vinekeepers.workflow.steps;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepResult;
import com.vinekeepers.workflow.WorkflowConditionEvaluator;
import com.vinekeepers.workflow.WorkflowStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Step that branches to another step index based on conditions (state key truthy, key/value equals,
 * or explicit operator with optional state-value transforms).
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
                String operator = (String) whenMap.get("operator");
                Object transformObj = whenMap.get("transform");
                List<String> transformList = toTransformList(transformObj);
                Object stateVal = key != null ? state.get(key) : null;
                if (WorkflowConditionEvaluator.evaluate(stateVal, operator, expectVal, transformList)) {
                    return clearKeys.isEmpty() ? StepResult.goTo(next) : StepResult.goTo(next, clearKeys);
                }
            } else if ("else".equals(whenObj)) {
                return clearKeys.isEmpty() ? StepResult.goTo(next) : StepResult.goTo(next, clearKeys);
            } else if (whenObj instanceof String stateKey && isTruthy(state.get(stateKey))) {
                return clearKeys.isEmpty() ? StepResult.goTo(next) : StepResult.goTo(next, clearKeys);
            }
        }
        return StepResult.goTo(stepIndex + 1);
    }

    private static List<String> toTransformList(Object transformObj) {
        if (transformObj == null) return List.of();
        if (transformObj instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object e : list) {
                if (e != null) out.add(e.toString().trim());
            }
            return out;
        }
        return List.of(transformObj.toString().trim());
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
