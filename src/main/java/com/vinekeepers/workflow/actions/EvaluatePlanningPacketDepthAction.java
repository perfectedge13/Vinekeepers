package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.planreview.PlanningPacketDepthEvaluator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sets {@code planningPacketDepthOk} and {@code planningPacketDepthReason} for workflow branching before
 * {@link PostPlanningPacketThreadAction}.
 */
public final class EvaluatePlanningPacketDepthAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStateStore;
    private final WorkProfileRegistry workProfileRegistry;

    public EvaluatePlanningPacketDepthAction(FeaturePlanStateStore planStateStore, WorkProfileRegistry workProfileRegistry) {
        this.planStateStore = planStateStore;
        this.workProfileRegistry = workProfileRegistry;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("planningPacketDepthOk", "false");
        spread.put("planningPacketDepthReason", "");
        spread.put("planningReviewReady", "false");
        spread.put("reviewReady", "false");
        spread.put("planningReviewReadyReason", "");
        spread.put("reviewReadyReason", "");
        if (planStateStore == null) {
            spread.put("planningPacketDepthReason", "Plan store not available.");
            return spread;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            spread.put("planningPacketDepthReason", "Missing contextId.");
            return spread;
        }
        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
        WorkProfileDefinition profile = null;
        if (workProfileRegistry != null && plan != null && plan.getProfileId() != null && !plan.getProfileId().isBlank()) {
            profile = workProfileRegistry.get(plan.getProfileId()).orElse(null);
        }
        boolean relaxOpenQ = PlanningPacketDepthEvaluator.relaxOpenQuestionSupplementalChecks(state);
        PlanningPacketDepthEvaluator.DepthResult r =
                PlanningPacketDepthEvaluator.evaluate(plan, profile, relaxOpenQ);
        spread.put("planningPacketDepthOk", r.ok() ? "true" : "false");
        spread.put("planningPacketDepthReason", r.reason() != null ? r.reason() : "");
        spread.put("planningPacketDepthRetryRecommended", r.ok() ? "false" : "true");
        boolean noClarify = !"true".equalsIgnoreCase(String.valueOf(state.get("planningUserInputRequired")));
        boolean rr = r.ok() && noClarify;
        spread.put("planningReviewReady", rr ? "true" : "false");
        spread.put("reviewReady", rr ? "true" : "false");
        if (rr) {
            spread.put(
                    "planningReviewReadyReason",
                    "Depth evaluation passed and no clarification is blocking — safe to post the review packet when other checks agree.");
            spread.put("reviewReadyReason", spread.get("planningReviewReadyReason"));
        } else {
            StringBuilder sb = new StringBuilder();
            if (!r.ok()) {
                sb.append(r.reason() != null ? r.reason() : "Depth check failed. ");
            }
            if (!noClarify) {
                sb.append("A clarification is still required before posting. ");
            }
            String msg = sb.toString().trim();
            spread.put("planningReviewReadyReason", msg.isEmpty() ? "Not review-ready yet." : msg);
            spread.put("reviewReadyReason", spread.get("planningReviewReadyReason"));
        }
        return spread;
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
