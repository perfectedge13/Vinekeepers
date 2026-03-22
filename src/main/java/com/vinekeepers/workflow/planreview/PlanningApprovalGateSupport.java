package com.vinekeepers.workflow.planreview;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanApprovalStatus;
import com.vinekeepers.state.planning.PlanConfidence;
import com.vinekeepers.state.planning.PlanCritiqueLifecycleStatus;
import com.vinekeepers.state.planning.PlanReadinessStatus;

import java.util.Map;

/** Shared canonical checks before launch approval is offered or persisted. */
public final class PlanningApprovalGateSupport {

    private PlanningApprovalGateSupport() {}

    public static boolean isApproveDecision(String rawDecision) {
        if (rawDecision == null || rawDecision.isBlank()) {
            return false;
        }
        String n = rawDecision.trim().toLowerCase();
        return "approve".equals(n) || "approve_with_risks".equals(n);
    }

    /**
     * @return null if allowed; otherwise a human-readable block reason
     */
    public static String validateApproveAllowed(FeaturePlanState plan, Map<String, Object> workflowState) {
        if (plan == null) {
            return "No plan state is loaded for this context.";
        }
        if (plan.getPacketPostedAt() == null) {
            return "NOT READY because the planning packet has not been recorded on the plan after a successful thread post.";
        }
        if (workflowState != null) {
            int v = parseInt(workflowState.get("planningPacketPostedVersion"), 0);
            if (v <= 0) {
                return "NOT READY because the workflow shows no posted packet version yet.";
            }
        }
        if (plan.getPlanCritiqueSnapshot() == null) {
            return "NOT READY because critique has not been persisted on the plan.";
        }
        if (!PlanCritiqueLifecycleStatus.COMPLETE.equalsIgnoreCase(plan.getCritiqueLifecycleStatus())) {
            return "NOT READY because critique lifecycle is not complete.";
        }
        var snap = plan.getPlanCritiqueSnapshot();
        if (snap.getGeneratedAt() == null) {
            return "NOT READY because critique timestamp is missing.";
        }
        PlanConfidence c = plan.getPlanConfidence();
        if (c == null || c.getReadinessStatus() == null || c.getReadinessStatus().isBlank()) {
            return "NOT READY because readiness has not been computed on the plan.";
        }
        if (!PlanReadinessStatus.READY.equals(c.getReadinessStatus())) {
            return "NOT READY because readiness is "
                    + c.getReadinessStatus()
                    + " (need READY after human proceed if applicable).";
        }
        if (c.getConfidenceScore() >= 0
                && c.getConfidenceScore() < PlanReadinessCalculator.APPROVAL_CONFIDENCE_THRESHOLD) {
            return "NOT READY because confidence score "
                    + String.format(java.util.Locale.ROOT, "%.2f", c.getConfidenceScore())
                    + " is below the threshold "
                    + PlanReadinessCalculator.APPROVAL_CONFIDENCE_THRESHOLD
                    + ".";
        }
        int bi = plan.countBlockingIssues();
        if (bi > 0) {
            return "NOT READY because " + bi + " blocking issue(s) remain (resolve or waive explicitly).";
        }
        return null;
    }

    public static String mapPersistStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String n = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (n) {
            case "approve" -> PlanApprovalStatus.APPROVE;
            case "approve_with_risks" -> PlanApprovalStatus.APPROVE_WITH_RISKS;
            case "revise" -> PlanApprovalStatus.REVISE;
            case "reject" -> PlanApprovalStatus.REJECT;
            default -> null;
        };
    }

    private static int parseInt(Object o, int dflt) {
        if (o == null) {
            return dflt;
        }
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }
}
