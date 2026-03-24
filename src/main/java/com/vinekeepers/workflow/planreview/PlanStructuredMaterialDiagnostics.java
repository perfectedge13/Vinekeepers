package com.vinekeepers.workflow.planreview;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanAssumption;
import com.vinekeepers.state.planning.PlanAssumptionStatus;
import com.vinekeepers.state.planning.PlanGovernanceSeverity;
import com.vinekeepers.state.planning.PlanIssue;
import com.vinekeepers.state.planning.PlanIssueStatus;

/**
 * Read-only diagnostics for structured plan material (unresolved questions, blocking issues, high-severity assumptions).
 * Not a routing authority — used for synthesis/readiness signals only.
 */
public final class PlanStructuredMaterialDiagnostics {

    private PlanStructuredMaterialDiagnostics() {}

    public static boolean hasStructuredMaterialPlanningGaps(FeaturePlanState plan) {
        return !firstStructuredMaterialQuestion(plan).isBlank();
    }

    public static String firstStructuredMaterialQuestion(FeaturePlanState plan) {
        if (plan == null) {
            return "";
        }
        for (String uq : plan.getUnresolvedQuestions()) {
            if (uq != null) {
                String t = uq.trim();
                if (!t.isBlank()) {
                    return t;
                }
            }
        }
        for (PlanIssue issue : plan.getIssues()) {
            if (!issue.isBlocking() || !PlanIssueStatus.OPEN.equalsIgnoreCase(issue.getStatus())) {
                continue;
            }
            String title = issue.getTitle() != null ? issue.getTitle().trim() : "";
            if (!title.isBlank()) {
                return title;
            }
            String detail = issue.getDetail() != null ? issue.getDetail().trim() : "";
            if (!detail.isBlank()) {
                return detail;
            }
        }
        for (PlanAssumption a : plan.getAssumptions()) {
            if (!PlanAssumptionStatus.OPEN.equalsIgnoreCase(a.getStatus())) {
                continue;
            }
            if (!PlanGovernanceSeverity.HIGH.equalsIgnoreCase(a.getSeverity())) {
                continue;
            }
            String s = a.getStatement() != null ? a.getStatement().trim() : "";
            if (!s.isBlank()) {
                String head = s.length() <= 220 ? s : s.substring(0, 219) + "…";
                return "Confirm or correct this high-severity assumption: " + head;
            }
        }
        return "";
    }
}
