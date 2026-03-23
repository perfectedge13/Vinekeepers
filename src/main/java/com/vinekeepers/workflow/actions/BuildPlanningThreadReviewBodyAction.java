package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.planreview.PlanningThreadPacketFormatter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds a thread-safe summary of drafted planning artifacts for pre-approval visibility.
 * Reads canonical artifact section values (same paths as proposal auto-apply).
 */
public final class BuildPlanningThreadReviewBodyAction implements com.vinekeepers.workflow.WorkflowAction {

    /** Max characters kept in workflow spread (full packet may be posted separately in chunks). */
    private static final int TOTAL_CAP = 12000;

    private final FeaturePlanStateStore planStore;

    public BuildPlanningThreadReviewBodyAction(FeaturePlanStateStore planStore) {
        this.planStore = planStore;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("planningThreadReviewBuildError", "");
        spread.put("planningThreadReviewBody", "");
        if (planStore == null) {
            spread.put("planningThreadReviewBuildError", "FeaturePlanStateStore not available.");
            return spread;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            spread.put("planningThreadReviewBuildError", "Missing contextId.");
            return spread;
        }
        FeaturePlanState plan = planStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            spread.put("planningThreadReviewBuildError", "No FeaturePlanState for context.");
            return spread;
        }

        String request = firstNonBlank(plan.getInitialRequest(), getString(state, "codeChange"));
        String repo = firstNonBlank(plan.getRepoRef(), getString(state, "project"));
        String body;
        if (packetCanonicalInThread(state)) {
            body = PlanningThreadPacketFormatter.buildConciseThreadReviewBodyAfterPacket(plan, request);
        } else {
            body =
                    truncate(
                            PlanningThreadPacketFormatter.buildFullPacketBody(
                                    plan, request, repo, getString(state, "planningRepoEvidenceJson")),
                            TOTAL_CAP);
        }

        spread.put("planningThreadReviewBody", body);
        return spread;
    }

    /**
     * True when the coordinator path has already posted (or intentionally skipped repost of) the full packet in this
     * thread, so follow-up review copy should not duplicate it.
     */
    private static boolean packetCanonicalInThread(Map<String, Object> state) {
        if (state == null) {
            return false;
        }
        if (truthy(String.valueOf(state.get("planningPacketPosted")))) {
            return true;
        }
        if (truthy(String.valueOf(state.get("planningPacketSkippedDuplicate")))) {
            return true;
        }
        return parsePostedVersion(state) > 0;
    }

    private static int parsePostedVersion(Map<String, Object> state) {
        Object v = state.get("planningPacketPostedVersion");
        if (v == null) {
            return 0;
        }
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean truthy(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim().toLowerCase();
        return "true".equals(t) || "1".equals(t) || "yes".equals(t);
    }

    private static String truncate(String s, int max) {
        if (s == null || s.isBlank()) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 1)
                + "…\n\n_(Planning thread review truncated to fit the workflow cap of "
                + max
                + " characters.)_";
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return a != null ? a : (b != null ? b : "");
    }
}
