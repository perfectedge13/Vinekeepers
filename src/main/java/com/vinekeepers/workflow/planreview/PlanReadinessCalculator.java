package com.vinekeepers.workflow.planreview;

import com.vinekeepers.state.planning.DiscoveryGap;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanAssumptionStatus;
import com.vinekeepers.state.planning.PlanConfidence;
import com.vinekeepers.state.planning.PlanCritiqueFinding;
import com.vinekeepers.state.planning.PlanCritiqueRubricScores;
import com.vinekeepers.state.planning.PlanGovernanceSeverity;
import com.vinekeepers.state.planning.PlanReadinessStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Evidence-based readiness and confidence derived from gaps, critique, rubric, and governance tallies.
 */
public final class PlanReadinessCalculator {

    public static final double APPROVAL_CONFIDENCE_THRESHOLD = 0.75;

    private PlanReadinessCalculator() {}

    public static PlanConfidence evaluate(
            FeaturePlanState plan,
            List<DiscoveryGap> gaps,
            List<PlanCritiqueFinding> findings,
            PlanCritiqueRubricScores rubric,
            Instant now,
            boolean packetPostedOnPlan) {

        List<String> reasons = new ArrayList<>();
        int blockingIssues = PlanCritiqueRubric.countUnresolvedBlockingIssues(plan);
        int blockingFindings = countBlockingFindings(findings);
        boolean openGaps = gaps != null && !gaps.isEmpty();
        boolean blockerGap =
                gaps != null && gaps.stream().anyMatch(g -> "BLOCKER".equalsIgnoreCase(g.getSeverity()));

        if (blockerGap) {
            reasons.add("At least one discovery gap is marked BLOCKER.");
        }
        if (!packetPostedOnPlan) {
            reasons.add("Planning packet has not been recorded on the plan state (post step may not have completed).");
        }
        if (blockingIssues > 0) {
            reasons.add(blockingIssues + " BLOCKING issue(s) remain on the plan.");
        }
        if (blockingFindings > 0) {
            reasons.add(blockingFindings + " critique finding(s) block approval.");
        }
        if (openGaps) {
            reasons.add("Open discovery gaps: " + gaps.size() + ".");
        }

        String readiness;
        if (blockerGap) {
            readiness = PlanReadinessStatus.BLOCKED;
        } else if (!packetPostedOnPlan
                || blockingIssues > 0
                || blockingFindings > 0
                || openGaps) {
            readiness = PlanReadinessStatus.NOT_READY;
        } else if (needsConditional(plan, findings)) {
            readiness = PlanReadinessStatus.CONDITIONALLY_READY;
            reasons.add("Residual assumptions or warnings require explicit human acknowledgment.");
        } else {
            readiness = PlanReadinessStatus.READY;
        }

        double base = rubric != null ? rubric.meanScore() : 0.5;
        base -= PlanCritiqueRubric.governanceAssumptionPenalty(plan);
        base -= Math.min(0.5, blockingIssues * 0.14);
        base -= Math.min(0.45, blockingFindings * 0.12);
        if (openGaps) {
            base -= 0.2;
        }
        if (!packetPostedOnPlan) {
            base -= 0.25;
        }
        double score = Math.max(0.0, Math.min(1.0, base));

        String level;
        if (score >= 0.82) {
            level = "HIGH";
        } else if (score >= 0.55) {
            level = "MEDIUM";
        } else {
            level = "LOW";
        }

        String notes = summarizeCounts(gaps, findings, plan);
        return new PlanConfidence(level, notes, readiness, now, score, List.copyOf(reasons));
    }

    private static boolean needsConditional(FeaturePlanState plan, List<PlanCritiqueFinding> findings) {
        if (plan != null) {
            long openHighAssumptions =
                    plan.getAssumptions().stream()
                            .filter(a -> PlanAssumptionStatus.OPEN.equals(a.getStatus()))
                            .filter(a -> PlanGovernanceSeverity.HIGH.equalsIgnoreCase(a.getSeverity()))
                            .count();
            if (openHighAssumptions > 0) {
                return true;
            }
            if (plan.getAssumptions().stream().filter(a -> PlanAssumptionStatus.OPEN.equals(a.getStatus())).count()
                    >= 4) {
                return true;
            }
            if (!plan.getIssues().isEmpty()) {
                return true;
            }
        }
        if (findings != null) {
            long warn = findings.stream().filter(f -> "WARN".equalsIgnoreCase(f.getSeverity())).count();
            if (warn >= 2) {
                return true;
            }
        }
        return false;
    }

    private static int countBlockingFindings(List<PlanCritiqueFinding> findings) {
        if (findings == null) {
            return 0;
        }
        int n = 0;
        for (PlanCritiqueFinding f : findings) {
            if (f.isBlocksApproval()) {
                n++;
            }
        }
        return n;
    }

    private static String summarizeCounts(
            List<DiscoveryGap> gaps, List<PlanCritiqueFinding> findings, FeaturePlanState plan) {
        StringBuilder sb = new StringBuilder();
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
