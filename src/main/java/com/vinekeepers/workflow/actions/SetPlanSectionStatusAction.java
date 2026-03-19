package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.PlanSectionKey;
import com.vinekeepers.state.planning.PlanSectionStatus;

import java.util.Locale;
import java.util.Map;

public final class SetPlanSectionStatusAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStateStore;

    public SetPlanSectionStatusAction(FeaturePlanStateStore planStateStore) {
        this.planStateStore = planStateStore;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        if (planStateStore == null) {
            return "FeaturePlanStateStore not available.";
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            return "Missing contextId for set_plan_section_status.";
        }
        String sectionRaw = firstNonBlank(getString(bind, "section"), getString(bind, "planSection"));
        if (sectionRaw == null || sectionRaw.isBlank()) {
            return "Missing section for set_plan_section_status.";
        }
        String statusRaw = firstNonBlank(getString(bind, "status"), getString(bind, "sectionStatus"));
        if (statusRaw == null || statusRaw.isBlank()) {
            return "Missing status for set_plan_section_status.";
        }
        PlanSectionKey key = parseSection(sectionRaw);
        if (key == null) {
            return "Invalid plan section: " + sectionRaw;
        }
        PlanSectionStatus st = parseStatus(statusRaw);
        if (st == null) {
            return "Invalid plan section status: " + statusRaw;
        }
        PlanSectionKey fk = key;
        PlanSectionStatus fst = st;
        return planStateStore.getByContextId(contextId)
                .map(p -> {
                    FeaturePlanState next = p.withSectionStatus(fk, fst);
                    planStateStore.update(next);
                    return "OK";
                })
                .orElse("No FeaturePlanState for contextId: " + contextId);
    }

    private static PlanSectionKey parseSection(String raw) {
        if (raw == null) {
            return null;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (PlanSectionKey k : PlanSectionKey.values()) {
            if (k.name().equals(u)) {
                return k;
            }
        }
        return null;
    }

    private static PlanSectionStatus parseStatus(String raw) {
        if (raw == null) {
            return null;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        for (PlanSectionStatus s : PlanSectionStatus.values()) {
            if (s.name().equals(u)) {
                return s;
            }
        }
        return null;
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
