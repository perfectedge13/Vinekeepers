package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.PlanApproval;
import com.vinekeepers.workflow.planreview.PlanningApprovalGateSupport;

import java.time.Instant;
import java.util.Map;

/**
 * Persists human plan approval decision from workflow state into {@link FeaturePlanState}.
 */
public final class PersistPlanApprovalAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStateStore;

    public PersistPlanApprovalAction(FeaturePlanStateStore planStateStore) {
        this.planStateStore = planStateStore;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        if (planStateStore == null) {
            return "FeaturePlanStateStore not available.";
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        String raw = firstNonBlank(getString(bind, "planApprovalDecision"), getString(state, "planApprovalDecision"));
        if (contextId == null || contextId.isBlank()) {
            return "Missing contextId for persist_plan_approval.";
        }
        if (raw == null || raw.isBlank()) {
            return "Missing planApprovalDecision.";
        }
        String status = PlanningApprovalGateSupport.mapPersistStatus(raw.trim());
        if (status == null) {
            return "Unknown planApprovalDecision: " + raw;
        }
        if (PlanningApprovalGateSupport.isApproveDecision(raw.trim())) {
            FeaturePlanState current = planStateStore.getByContextId(contextId).orElse(null);
            String block = PlanningApprovalGateSupport.validateApproveAllowed(current, state);
            if (block != null) {
                return block;
            }
        }
        String actorId = resolveActorId(bind, state);
        Instant at = Instant.now();
        PlanApproval approval = new PlanApproval(status, actorId, at, "");
        return planStateStore.getByContextId(contextId)
                .map(p -> {
                    FeaturePlanState next = p.withPlanApproval(approval);
                    planStateStore.update(next);
                    return "OK";
                })
                .orElse("No FeaturePlanState for contextId: " + contextId);
    }

    private static String resolveActorId(Map<String, Object> bind, Map<String, Object> state) {
        @SuppressWarnings("unchecked")
        Map<String, Object> ev = bind != null ? castMap(bind.get("__event")) : null;
        if (ev == null && state != null) {
            ev = castMap(state.get("__event"));
        }
        if (ev != null) {
            String a = getString(ev, "authorId");
            if (a != null && !a.isBlank()) {
                return a;
            }
        }
        return "";
    }

    private static Map<String, Object> castMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) m;
            return typed;
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
