package com.vinekeepers.workflow.planning;

import java.util.Locale;
import java.util.Map;

/**
 * Cycle-time readiness hints before packet post + critique compute the authoritative review / approval flags.
 */
public final class PlanningReadinessSpread {

    public static final String PACKET_POSTING_ALLOWED_KEY = "planningPacketPostingAllowed";
    public static final String REVIEW_ALLOWED_KEY = "planningReviewAllowed";
    public static final String APPROVAL_ALLOWED_KEY = "planningApprovalAllowed";
    public static final String HUMAN_READINESS_ACKNOWLEDGED_KEY = "planningHumanReadinessAcknowledged";

    private PlanningReadinessSpread() {}

    /**
     * After a planning cycle: packet posting may be allowed, but final review/approval readiness is decided only after
     * packet post + critique gates run. Reads material pacing booleans from {@link PlanningMaterialSpreadKeys}.
     */
    public static void applyCycleReadiness(Map<String, Object> spread) {
        boolean packetAllowed = truthy(spread, PlanningMaterialSpreadKeys.MATERIAL_PACKET_POSTING_ALLOWED_KEY);
        boolean readyToPost = truthy(spread, PlanningMaterialSpreadKeys.MATERIAL_READY_FOR_PACKET_KEY);
        boolean userInputRequired = truthy(spread, PlanningMaterialSpreadKeys.MATERIAL_USER_INPUT_REQUIRED_KEY);
        spread.put(PACKET_POSTING_ALLOWED_KEY, packetAllowed ? "true" : "false");
        spread.put(REVIEW_ALLOWED_KEY, (!userInputRequired && packetAllowed) ? "true" : "false");
        spread.put(APPROVAL_ALLOWED_KEY, "false");
        if (userInputRequired) {
            spread.put(
                    "planningReviewReadyReason",
                    "Waiting on a clarification answer before packet posting, review, or approval can continue.");
            spread.put(
                    "reviewReadyReason",
                    spread.get("planningReviewReadyReason"));
        } else if (packetAllowed && readyToPost) {
            spread.put(
                    "planningReviewReadyReason",
                    "Packet posting is allowed; final review readiness is computed after the packet posts and critique runs.");
            spread.put("reviewReadyReason", spread.get("planningReviewReadyReason"));
        } else if (packetAllowed) {
            spread.put(
                    "planningReviewReadyReason",
                    "Packet posting may continue under assumption-handling, but final review readiness is still pending.");
            spread.put("reviewReadyReason", spread.get("planningReviewReadyReason"));
        } else {
            spread.put(
                    "planningReviewReadyReason",
                    "Draft or clarification gates still need another pass before packet posting can continue.");
            spread.put("reviewReadyReason", spread.get("planningReviewReadyReason"));
        }
        spread.put(
                "planningApprovalReadyReason",
                "Approval is evaluated only after the packet is posted and critique readiness has run.");
        spread.put("approvalReadyReason", spread.get("planningApprovalReadyReason"));
    }

    public static boolean hasPendingClarification(Map<String, ?> map) {
        return PlanningNextAction.ASK_USER.name().equalsIgnoreCase(getString(map, PlanningRoutingBridge.NEXT_ACTION_KEY))
                || "WAITING_FOR_TEXT_REPLY".equalsIgnoreCase(getString(map, PlanningCanonicalDecisionSupport.CANONICAL_INTERACTION_STATE_KEY))
                || "CLARIFYING".equalsIgnoreCase(getString(map, PlanningCanonicalDecisionSupport.CANONICAL_STAGE_KEY))
                || truthy(map, "planningCanonicalUserInputRequired");
    }

    public static boolean packetPostingAllowed(Map<String, ?> map) {
        return "READY_FOR_PACKET".equalsIgnoreCase(getString(map, PlanningRoutingBridge.NEXT_ACTION_KEY))
                && truthy(map, PACKET_POSTING_ALLOWED_KEY);
    }

    public static boolean humanReadinessAcknowledged(Map<String, ?> map) {
        return truthy(map, HUMAN_READINESS_ACKNOWLEDGED_KEY)
                || "proceed".equalsIgnoreCase(getString(map, "readinessProceedRaw"));
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

    private static String getString(Map<String, ?> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
