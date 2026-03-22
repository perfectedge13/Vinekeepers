package com.vinekeepers.workflow.planning;

import java.util.Locale;
import java.util.Map;

/**
 * Dual-writes generic readiness flags ({@code reviewReady}, {@code approvalReady}) alongside legacy {@code planning*} keys.
 */
public final class PlanningReadinessSpread {

    private PlanningReadinessSpread() {}

    /**
     * After a planning cycle: review aligns with “ok to move toward posting / no clarification blocking”; approval is not
     * decided here.
     */
    public static void applyCycleReadiness(Map<String, Object> spread, boolean readyToPost, boolean userInputRequired) {
        String rr = readyToPost ? "true" : "false";
        spread.put("planningReviewReady", rr);
        spread.put("reviewReady", rr);
        spread.put("planningApprovalReady", "false");
        spread.put("approvalReady", "false");
        if (userInputRequired) {
            spread.put(
                    "planningReviewReadyReason",
                    "Waiting on a clarification answer before the draft is stable enough to treat as review-ready.");
            spread.put(
                    "reviewReadyReason",
                    spread.get("planningReviewReadyReason"));
        } else if (readyToPost) {
            spread.put(
                    "planningReviewReadyReason",
                    "Depth check passed inside this cycle and no blocking clarification is open.");
            spread.put("reviewReadyReason", spread.get("planningReviewReadyReason"));
        } else {
            spread.put(
                    "planningReviewReadyReason",
                    "Draft or depth checks still need another pass inside the planning cycle.");
            spread.put("reviewReadyReason", spread.get("planningReviewReadyReason"));
        }
        spread.put(
                "planningApprovalReadyReason",
                "Approval is evaluated only after the packet is posted and critique readiness has run.");
        spread.put("approvalReadyReason", spread.get("planningApprovalReadyReason"));
    }

    public static boolean truthy(Map<String, ?> map, String key) {
        if (map == null || key == null) {
            return false;
        }
        Object v = map.get(key);
        if (v == null) {
            return false;
        }
        String t = v.toString().trim().toLowerCase(Locale.ROOT);
        return "true".equals(t) || "1".equals(t) || "yes".equals(t);
    }
}
