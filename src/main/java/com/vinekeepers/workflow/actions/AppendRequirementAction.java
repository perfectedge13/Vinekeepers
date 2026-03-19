package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.RequirementEntry;

import java.util.Map;
import java.util.UUID;

public final class AppendRequirementAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStateStore;

    public AppendRequirementAction(FeaturePlanStateStore planStateStore) {
        this.planStateStore = planStateStore;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        if (planStateStore == null) {
            return "FeaturePlanStateStore not available.";
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            return "Missing contextId for append_plan_requirement.";
        }
        String text = firstNonBlank(getString(bind, "text"), getString(bind, "requirementText"));
        if (text == null || text.isBlank()) {
            return "Missing text for append_plan_requirement.";
        }
        String id = firstNonBlank(getString(bind, "id"), getString(bind, "requirementId"));
        if (id == null || id.isBlank()) {
            id = "req-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        final String entryId = id;
        return planStateStore.getByContextId(contextId)
                .map(p -> {
                    FeaturePlanState next = p.withAppendedRequirement(new RequirementEntry(entryId, text, null));
                    planStateStore.update(next);
                    return "OK";
                })
                .orElse("No FeaturePlanState for contextId: " + contextId);
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
