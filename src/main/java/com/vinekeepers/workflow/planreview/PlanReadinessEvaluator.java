package com.vinekeepers.workflow.planreview;

import com.vinekeepers.state.planning.DiscoveryGap;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanConfidence;
import com.vinekeepers.state.planning.PlanCritiqueFinding;
import com.vinekeepers.state.planning.PlanCritiqueRubricScores;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Derives machine readiness and confidence from gaps and critique findings (delegates to {@link PlanReadinessCalculator}).
 */
public final class PlanReadinessEvaluator {

    private PlanReadinessEvaluator() {}

    public static PlanConfidence evaluate(
            FeaturePlanState plan,
            List<DiscoveryGap> gaps,
            List<PlanCritiqueFinding> findings,
            Instant now) {
        return evaluate(plan, gaps, findings, now, null);
    }

    /**
     * @param workflowState optional workflow map for {@code planningPacketPosted} / {@code planningPacketPostedVersion};
     *                        when null, packet-posted is inferred true for backward-compatible unit tests.
     */
    public static PlanConfidence evaluate(
            FeaturePlanState plan,
            List<DiscoveryGap> gaps,
            List<PlanCritiqueFinding> findings,
            Instant now,
            Map<String, Object> workflowState) {
        int blockingFc = 0;
        if (findings != null) {
            for (PlanCritiqueFinding f : findings) {
                if (f.isBlocksApproval()) {
                    blockingFc++;
                }
            }
        }
        PlanCritiqueRubricScores rubric = PlanCritiqueRubric.compute(plan, findings, blockingFc);
        boolean posted = inferPacketPostedOnPlan(plan, workflowState);
        return PlanReadinessCalculator.evaluate(plan, gaps, findings, rubric, now, posted);
    }

    static boolean inferPacketPostedOnPlan(FeaturePlanState plan, Map<String, Object> workflowState) {
        if (plan != null && plan.getPacketPostedAt() != null) {
            return true;
        }
        if (workflowState == null) {
            return true;
        }
        if ("true".equalsIgnoreCase(String.valueOf(workflowState.get("planningPacketPosted")))) {
            return true;
        }
        Object v = workflowState.get("planningPacketPostedVersion");
        if (v == null) {
            return false;
        }
        try {
            return Integer.parseInt(v.toString().trim()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
