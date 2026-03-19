package com.vinekeepers.state.planning;

/**
 * Canonical plan approval outcomes (Phase C).
 */
public final class PlanApprovalStatus {

    public static final String PENDING = "PENDING";
    public static final String APPROVE = "APPROVE";
    public static final String APPROVE_WITH_RISKS = "APPROVE_WITH_RISKS";
    public static final String REVISE = "REVISE";
    public static final String REJECT = "REJECT";

    private PlanApprovalStatus() {}

    public static boolean allowsLaunch(String status) {
        return APPROVE.equalsIgnoreCase(status) || APPROVE_WITH_RISKS.equalsIgnoreCase(status);
    }
}
