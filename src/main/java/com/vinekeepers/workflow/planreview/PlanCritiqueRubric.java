package com.vinekeepers.workflow.planreview;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanAssumptionStatus;
import com.vinekeepers.state.planning.PlanCritiqueFinding;
import com.vinekeepers.state.planning.PlanCritiqueRubricScores;
import com.vinekeepers.state.planning.PlanGovernanceSeverity;
import com.vinekeepers.state.planning.PlanIssueStatus;

import java.util.List;

/** Derives normalized rubric scores from critique findings and governance tallies. */
public final class PlanCritiqueRubric {

    private PlanCritiqueRubric() {}

    public static PlanCritiqueRubricScores compute(
            FeaturePlanState plan, List<PlanCritiqueFinding> findings, int blockingFindings) {
        int must = 0;
        int warn = 0;
        int info = 0;
        if (findings != null) {
            for (PlanCritiqueFinding f : findings) {
                String s = f.getSeverity() != null ? f.getSeverity().toUpperCase() : "";
                if ("MUST_FIX".equals(s) || "BLOCKER".equals(s)) {
                    must++;
                } else if ("WARN".equals(s)) {
                    warn++;
                } else {
                    info++;
                }
            }
        }
        double penalty = Math.min(1.0, must * 0.22 + warn * 0.08 + info * 0.02);
        double baseCompleteness = 1.0 - Math.min(0.95, must * 0.25 + warn * 0.1);
        double consistency = 1.0 - Math.min(0.9, must * 0.2);
        double clarity = 1.0 - Math.min(0.85, must * 0.18 + warn * 0.06);
        double repo = repoAlignmentScore(plan, must, warn);
        double plausibility = 1.0 - Math.min(0.9, must * 0.2 + warn * 0.05);
        double testability = 1.0 - Math.min(0.85, must * 0.15 + warn * 0.07);
        double approval = Math.max(0.0, 1.0 - penalty - (blockingFindings > 0 ? 0.35 : 0.0));

        return new PlanCritiqueRubricScores(
                baseCompleteness, consistency, clarity, repo, plausibility, testability, approval);
    }

    private static double repoAlignmentScore(FeaturePlanState plan, int must, int warn) {
        double score = 1.0;
        if (plan != null) {
            String ws = plan.getRepoWorkspaceStatus() != null ? plan.getRepoWorkspaceStatus().trim() : "";
            if ("FAILED".equalsIgnoreCase(ws) || "UNAVAILABLE".equalsIgnoreCase(ws)) {
                score -= 0.45;
            } else if (ws.isBlank()) {
                score -= 0.05;
            }
            if (plan.getRepoLocalPath() == null || plan.getRepoLocalPath().isBlank()) {
                score -= 0.08;
            }
        }
        score -= Math.min(0.5, must * 0.12 + warn * 0.04);
        return Math.max(0.0, score);
    }

    public static double governanceAssumptionPenalty(FeaturePlanState plan) {
        if (plan == null || plan.getAssumptions().isEmpty()) {
            return 0.0;
        }
        double p = 0.0;
        for (var a : plan.getAssumptions()) {
            if (PlanAssumptionStatus.OPEN.equals(a.getStatus())) {
                String sev = a.getSeverity() != null ? a.getSeverity() : "";
                if (PlanGovernanceSeverity.HIGH.equalsIgnoreCase(sev)) {
                    p += 0.07;
                } else if (PlanGovernanceSeverity.MEDIUM.equalsIgnoreCase(sev)) {
                    p += 0.04;
                } else {
                    p += 0.02;
                }
            }
        }
        return Math.min(0.35, p);
    }

    public static int countUnresolvedBlockingIssues(FeaturePlanState plan) {
        if (plan == null) {
            return 0;
        }
        int n = 0;
        for (var i : plan.getIssues()) {
            if (PlanIssueStatus.BLOCKING.equalsIgnoreCase(i.getStatus())) {
                n++;
            }
        }
        return n;
    }
}
