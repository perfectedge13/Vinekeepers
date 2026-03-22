package com.vinekeepers.state.planning;

/** Shared lifecycle labels for {@link PlanRisk} and {@link PlanDecision} records. */
public final class PlanRiskDecisionStatus {

    public static final String OPEN = "OPEN";
    public static final String RESOLVED = "RESOLVED";
    public static final String WAIVED = "WAIVED";

    private PlanRiskDecisionStatus() {}
}
