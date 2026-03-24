package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.PlanningCanonicalDecision;
import com.vinekeepers.state.planning.PlanningCanonicalNextAction;
import com.vinekeepers.state.planning.PlanningIntakeStage;
import com.vinekeepers.state.planning.PlanningInteractionState;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningProjectionAdaptersTest {

    @Test
    void materialSpreadDefaults_excludesDeprecatedPlanningUserInputRequiredAlias() {
        Map<String, Object> m = PlanningMaterialSpreadDefaults.newPlanningCycleBaseSpread();
        assertFalse(m.containsKey("planningUserInputRequired"));
    }

    @Test
    void clarificationAdapter_fromCanonicalAskUser_doesNotFabricateQuestionWhenCanonicalEmpty() {
        PlanningCanonicalDecision ask =
                PlanningCanonicalDecision.create(
                        "t",
                        PlanningIntakeStage.CLARIFYING,
                        PlanningCanonicalNextAction.ASK_USER,
                        PlanningInteractionState.WAITING_FOR_TEXT_REPLY,
                        "MATERIALIZED",
                        "",
                        false,
                        false,
                        false,
                        "",
                        "",
                        "",
                        "",
                        List.of(),
                        "fp");
        Map<String, Object> spread = new LinkedHashMap<>();
        PlanningClarificationProjectionAdapter.applyFromCanonicalDecision(spread, ask);
        assertEquals("", spread.get("planningClarificationQuestionText"));
    }

    @Test
    void clarificationAdapter_preCanonicalEvaluation_doesNotWriteCanonicalNextAction() {
        ClarificationProjection proj = ClarificationProjection.fromSelection(CanonicalClarificationSelection.none());
        PlanningDeliberationLedgerSync.UpsertResult upsert =
                PlanningDeliberationLedgerSync.upsertOpenQuestion(UnresolvedItemLedger.empty(), proj);
        PlanningDecisionSnapshot snapshot =
                new PlanningDecisionSnapshot(PlanningNextAction.ASK_USER, 50, "Which option?", "", "MATERIALIZED", "");
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put(PlanningCanonicalDecisionSupport.CANONICAL_NEXT_ACTION_KEY, "BLOCK");
        PlanningClarificationProjectionAdapter.applyPreCanonicalEvaluationClarification(spread, upsert, proj, snapshot);
        assertEquals("BLOCK", spread.get(PlanningCanonicalDecisionSupport.CANONICAL_NEXT_ACTION_KEY));
    }

    @Test
    void clarificationAdapter_structuredOverlay_doesNotInventQuestionBeyondProjection() {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("planningClarificationQuestionText", "prior");
        ClarificationProjection emptyProj = ClarificationProjection.fromSelection(CanonicalClarificationSelection.none());
        PlanningClarificationProjectionAdapter.applyStructuredClarificationOverlayForAskUser(spread, emptyProj);
        assertEquals("", spread.get("planningClarificationQuestionText"));
    }

    @Test
    void repeatTracker_normalizesForComparison() {
        assertEquals(
                "hello world",
                PlanningClarificationRepeatTracker.normalizeForRepeatCompare("  Hello   \n world  "));
    }

    @Test
    void progressProjection_progressFingerprint_isDeterministicForSameBody() {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("planningCycleProgressSummary", "Planning cycle 2 — next: BLOCK");
        spread.put("userCopyCoordinatorProgress", "Planning cycle 2 — next: BLOCK");
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("planningLastProgressPostHash", "");
        PlanningProgressProjection.applyProgressFingerprint(state, spread);
        String fp1 = (String) spread.get("planningProgressPostFingerprint");
        spread.put("planningProgressPostFingerprint", "");
        PlanningProgressProjection.applyProgressFingerprint(state, spread);
        assertEquals(fp1, spread.get("planningProgressPostFingerprint"));
    }

    @Test
    void pipelineProjectionTail_reflectsCanonicalNextActionInProgressSummaryOnly() {
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        String contextId = "ctx-pipe-proj";

        PlanningCanonicalDecision canonical =
                PlanningCanonicalDecision.create(
                        "planning_evaluation",
                        PlanningIntakeStage.DRAFTING,
                        PlanningCanonicalNextAction.CONTINUE_SYNTHESIS,
                        PlanningInteractionState.NONE,
                        "MATERIALIZED",
                        "",
                        false,
                        false,
                        false,
                        "",
                        "",
                        "",
                        "",
                        List.of(),
                        "fp");

        PlanningEvaluationDecision decision = minimalDecision(false);

        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put(PlanningCanonicalDecisionSupport.CANONICAL_NEXT_ACTION_KEY, "CONTINUE_SYNTHESIS");
        spread.put(PlanningCanonicalDecisionSupport.CANONICAL_STAGE_KEY, "DRAFTING");
        Map<String, Object> state = new LinkedHashMap<>();

        PlanningPipelineProjectionAdapter.applyPostCanonicalEvaluationTail(
                spread, state, decision, canonical, store, contextId, 3);

        assertEquals("CONTINUE_SYNTHESIS", spread.get(PlanningCanonicalDecisionSupport.CANONICAL_NEXT_ACTION_KEY));
        String summary = (String) spread.get("planningCycleProgressSummary");
        assertTrue(summary.contains("BLOCKED"), summary);
    }

    private static PlanningEvaluationDecision minimalDecision(boolean success) {
        return new PlanningEvaluationDecision(
                success,
                PlanningCanonicalNextAction.BLOCK,
                PlanningIntakeStage.GATHERING_CONTEXT,
                PlanningInteractionState.NONE,
                "",
                new PlanningEvaluationDecision.EvaluationConfidence(50, "medium", "x"),
                List.of(),
                false,
                new PlanningEvaluationDecision.EvaluationBestQuestion("", ""),
                "",
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                false,
                false,
                false,
                "",
                "",
                false,
                false,
                "");
    }
}
