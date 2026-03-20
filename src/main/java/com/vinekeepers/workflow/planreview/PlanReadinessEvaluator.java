package com.vinekeepers.workflow.planreview;

import com.vinekeepers.state.planning.DiscoveryGap;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanConfidence;
import com.vinekeepers.state.planning.PlanCritiqueFinding;
import com.vinekeepers.state.planning.PlanReadinessStatus;

import java.time.Instant;
import java.util.List;

/**
 * Derives machine readiness and a coarse confidence level from gaps and critique findings.
 */
public final class PlanReadinessEvaluator {

    private PlanReadinessEvaluator() {}

    public static PlanConfidence evaluate(
            FeaturePlanState plan,
            List<DiscoveryGap> gaps,
            List<PlanCritiqueFinding> findings,
            Instant now) {
        String readiness = computeReadiness(gaps, findings, plan);
        String level = deriveLevel(findings, readiness);
        String notes = summarize(gaps, findings, plan);
        return new PlanConfidence(level, notes, readiness, now);
    }

    private static String computeReadiness(
            List<DiscoveryGap> gaps,
            List<PlanCritiqueFinding> findings,
            FeaturePlanState plan) {
        boolean blockerGap = gaps != null
                && gaps.stream().anyMatch(g -> "BLOCKER".equalsIgnoreCase(g.getSeverity()));
        if (blockerGap) {
            return PlanReadinessStatus.BLOCKED;
        }
        boolean openGaps = gaps != null && !gaps.isEmpty();
        if (openGaps) {
            return PlanReadinessStatus.NEEDS_REVISION;
        }
        boolean mustFixFinding = findings != null
                && findings.stream().anyMatch(f -> "MUST_FIX".equalsIgnoreCase(f.getSeverity())
                        || "BLOCKER".equalsIgnoreCase(f.getSeverity()));
        if (mustFixFinding) {
            return PlanReadinessStatus.NEEDS_REVISION;
        }
        boolean humanHint = false;
        if (plan != null && !plan.getIssues().isEmpty()) {
            humanHint = true;
        }
        if (findings != null) {
            long warn = findings.stream().filter(f -> "WARN".equalsIgnoreCase(f.getSeverity())).count();
            if (warn >= 2) {
                humanHint = true;
            }
        }
        if (plan != null && plan.getAssumptions().size() >= 4) {
            humanHint = true;
        }
        if (humanHint) {
            return PlanReadinessStatus.NEEDS_HUMAN_DECISION;
        }
        return PlanReadinessStatus.READY;
    }

    private static String deriveLevel(List<PlanCritiqueFinding> findings, String readiness) {
        if (PlanReadinessStatus.BLOCKED.equals(readiness)) {
            return "LOW";
        }
        if (PlanReadinessStatus.NEEDS_REVISION.equals(readiness)) {
            return "LOW";
        }
        if (findings == null || findings.isEmpty()) {
            return "HIGH";
        }
        boolean anyWarn = findings.stream().anyMatch(f -> "WARN".equalsIgnoreCase(f.getSeverity()));
        return anyWarn ? "MEDIUM" : "HIGH";
    }

    private static String summarize(
            List<DiscoveryGap> gaps,
            List<PlanCritiqueFinding> findings,
            FeaturePlanState plan) {
        StringBuilder sb = new StringBuilder();
        // Readiness status is exposed separately as planReadinessStatus; keep notes as detail counts only.
        if (gaps != null && !gaps.isEmpty()) {
            sb.append("Open gaps: ").append(gaps.size()).append(". ");
        }
        if (findings != null && !findings.isEmpty()) {
            sb.append("Critique findings: ").append(findings.size()).append(". ");
        }
        if (plan != null) {
            if (!plan.getIssues().isEmpty()) {
                sb.append("Issues: ").append(plan.getIssues().size()).append(". ");
            }
            if (!plan.getAssumptions().isEmpty()) {
                sb.append("Assumptions: ").append(plan.getAssumptions().size()).append(". ");
            }
        }
        return sb.toString().trim();
    }
}
