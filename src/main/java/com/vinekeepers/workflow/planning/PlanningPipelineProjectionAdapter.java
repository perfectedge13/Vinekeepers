package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanningCanonicalDecision;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.workflow.deliberation.DeliberationEngine;

import java.util.Map;

/**
 * Workflow-facing spread updates after material pacing and canonical projection: deliberation derivatives, assumption
 * counts, progress copy, clarification repeat bookkeeping, and cycle completion flags. Does not run evaluation or infer
 * next actions.
 */
public final class PlanningPipelineProjectionAdapter {

    private PlanningPipelineProjectionAdapter() {}

    public static void applyPostCanonicalEvaluationTail(
            Map<String, Object> spread,
            Map<String, Object> state,
            PlanningEvaluationDecision decision,
            PlanningCanonicalDecision canonical,
            FeaturePlanStateStore planStateStore,
            String contextId,
            int cycleIteration) {
        DeliberationEngine.applyDerivedDeliberationSpread(spread);
        spread.put("planningAssumptionsUsed", String.valueOf(decision.assumptionsToAdd().size()));
        String nextActionName =
                PlanningProgressProjection.firstNonBlank(
                        PlanningProgressProjection.getString(spread, PlanningRoutingBridge.NEXT_ACTION_KEY),
                        PlanningRoutingBridge.snapshotFromCanonical(canonical).nextAction().name());
        PlanningProgressProjection.applyCycleProgressSummaryAfterEvaluation(
                spread, cycleIteration, nextActionName);
        if (!decision.success()) {
            PlanningProgressProjection.applyUserVisibleEvaluationFailure(spread, decision.machineError());
        }
        PlanningClarificationRepeatTracker.apply(
                spread, state, UnresolvedItemLedger.readFrom(spread), canonical.nextAction());
        PlanningProgressProjection.applyUserCopyAndProgressLog(state, spread);
        spread.put("planningJustMergedClarification", "false");
        spread.put("planningSelectiveRerunActive", "false");
        PlanningProgressProjection.applyProgressFingerprint(state, spread);
        if (decision.success()) {
            spread.put("planningAutonomousFirstPassCompleted", "true");
            FeaturePlanState persisted = planStateStore.getByContextId(contextId).orElse(null);
            if (persisted != null) {
                planStateStore.update(persisted.withAutonomousPlanningPassCompleted(true));
            }
        }
    }
}
