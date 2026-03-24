package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.PlanningCanonicalDecision;
import com.vinekeepers.state.planning.PlanningCanonicalNextAction;
import com.vinekeepers.state.planning.PlanningIntakeStage;
import com.vinekeepers.state.planning.PlanningInteractionState;

import java.util.List;
import java.util.Map;

/**
 * The only bridge between live routing snapshot keys and persisted canonical planning decisions.
 */
public final class PlanningRoutingBridge {

    public static final String NEXT_ACTION_KEY = "planningNextAction";
    /** Graph phase id after evaluation; branches {@code rs_evaluation_branch} in arrietty_room_v2. */
    public static final String NEXT_PHASE_KEY = "planningNextPhase";

    public static final String PHASE_PLANNING_CLARIFICATION = "planning_clarification";
    public static final String PHASE_PLANNING_PACKET = "planning_packet";
    public static final String PHASE_PLANNING_BLOCKED = "planning_blocked";

    public static final String LAST_CONFIDENCE_KEY = "planningLastConfidence";
    public static final String NEXT_QUESTION_KEY = "planningNextQuestion";
    public static final String BLOCKING_REASON_KEY = "planningBlockingReason";
    public static final String REPO_EVIDENCE_STATUS_KEY = "planningRepoEvidenceStatus";
    public static final String DECISION_SUMMARY_KEY = "planningDecisionSummary";

    private PlanningRoutingBridge() {}

    /**
     * Clears live routing keys to neutral values so a new evaluation cycle does not inherit stale route signals.
     * Does not impersonate a semantic {@link PlanningNextAction#BLOCKED} result from the evaluator.
     */
    public static void clearStaleLiveRoutingKeysForEvaluationCycle(Map<String, Object> spread) {
        if (spread == null) {
            return;
        }
        spread.put(NEXT_ACTION_KEY, "");
        spread.put(NEXT_PHASE_KEY, "");
        spread.put(LAST_CONFIDENCE_KEY, "");
        spread.put(NEXT_QUESTION_KEY, "");
        spread.put(BLOCKING_REASON_KEY, "");
        spread.put(REPO_EVIDENCE_STATUS_KEY, "");
        spread.put(DECISION_SUMMARY_KEY, "");
        spread.put(PlanningCanonicalDecisionSupport.CANONICAL_NEXT_ACTION_KEY, "");
        spread.put(PlanningCanonicalDecisionSupport.CANONICAL_STAGE_KEY, "");
        spread.put(PlanningCanonicalDecisionSupport.CANONICAL_INTERACTION_STATE_KEY, "");
        spread.put(PlanningCanonicalDecisionSupport.CANONICAL_DECISION_SOURCE_KEY, "");
        spread.put(PlanningCanonicalDecisionSupport.CANONICAL_DECISION_ID_KEY, "");
        spread.put(PlanningCanonicalDecisionSupport.CANONICAL_DECISION_VERSION_KEY, "");
        spread.put(PlanningCanonicalDecisionSupport.CANONICAL_DECISION_NORMALIZED_AT_KEY, "");
        spread.put("planningCanonicalRepoGroundingState", "");
        spread.put("planningCanonicalConfidenceSummary", "");
        spread.put(PlanningCanonicalDecisionSupport.CANONICAL_TOP_GAP_ID_KEY, "");
        spread.put(PlanningCanonicalDecisionSupport.CANONICAL_TOP_GAP_TEXT_KEY, "");
        spread.put("planningCanonicalBlockingReason", "");
        spread.put(PlanningReadinessSpread.PACKET_POSTING_ALLOWED_KEY, "false");
        spread.put(PlanningReadinessSpread.REVIEW_ALLOWED_KEY, "false");
        spread.put(PlanningReadinessSpread.APPROVAL_ALLOWED_KEY, "false");
        spread.put(PlanningCanonicalDecisionSupport.LAST_MATERIAL_CHANGE_FP_KEY, "");
    }

    /**
     * Neutral seed for a planning cycle spread (synthesis + evaluation). Prefer over legacy {@code BLOCKED} defaults.
     */
    public static void seedNeutralCycleRoutingKeys(Map<String, Object> spread) {
        if (spread == null) {
            return;
        }
        clearStaleLiveRoutingKeysForEvaluationCycle(spread);
        spread.put("planningCanonicalUserInputRequired", "false");
        spread.put("planningClarificationQuestionText", "");
        spread.put("planningClarificationChoicesJson", "[]");
        spread.put("planningClarificationMetaJson", "{}");
        spread.put("planningClarificationOrchestratorPrompt", "");
        spread.put("planningClarificationUseStructuredChoices", "false");
        spread.put(PlanningMaterialSpreadKeys.MATERIAL_ACTION_KEY, PlanningSynthesisAction.CONTINUE_SYNTHESIS.name());
        spread.put(PlanningMaterialSpreadKeys.MATERIAL_USER_INPUT_REQUIRED_KEY, "false");
        spread.put(PlanningMaterialSpreadKeys.MATERIAL_READY_FOR_PACKET_KEY, "false");
        spread.put(PlanningMaterialSpreadKeys.MATERIAL_REVISION_NEEDED_KEY, "false");
        spread.put(PlanningMaterialSpreadKeys.MATERIAL_PACKET_POSTING_ALLOWED_KEY, "false");
    }

    /** @deprecated Use {@link #seedNeutralCycleRoutingKeys(Map)}; kept for any external callers. */
    @Deprecated
    public static void clearLiveRoutingKeys(Map<String, Object> spread) {
        seedNeutralCycleRoutingKeys(spread);
    }

    public static String planningPhaseIdForNextAction(PlanningNextAction action) {
        if (action == null) {
            return "";
        }
        return switch (action) {
            case ASK_USER -> PHASE_PLANNING_CLARIFICATION;
            case READY_FOR_PACKET -> PHASE_PLANNING_PACKET;
            case BLOCKED -> PHASE_PLANNING_BLOCKED;
        };
    }

    public static void projectSnapshotToSpread(Map<String, Object> spread, PlanningDecisionSnapshot snapshot) {
        if (spread == null || snapshot == null) {
            return;
        }
        spread.put(NEXT_ACTION_KEY, snapshot.nextAction().name());
        spread.put(NEXT_PHASE_KEY, planningPhaseIdForNextAction(snapshot.nextAction()));
        spread.put(LAST_CONFIDENCE_KEY, snapshot.lastConfidence() != null ? String.valueOf(snapshot.lastConfidence()) : "");
        spread.put(NEXT_QUESTION_KEY, snapshot.nextAction() == PlanningNextAction.ASK_USER ? snapshot.nextQuestion() : "");
        spread.put(BLOCKING_REASON_KEY, snapshot.nextAction() == PlanningNextAction.BLOCKED ? snapshot.blockingReason() : "");
        spread.put(REPO_EVIDENCE_STATUS_KEY, snapshot.repoEvidenceStatus());
        spread.put(DECISION_SUMMARY_KEY, snapshot.decisionSummary());
    }

    public static PlanningDecisionSnapshot snapshotFromCanonical(PlanningCanonicalDecision decision) {
        if (decision == null) {
            return new PlanningDecisionSnapshot(PlanningNextAction.BLOCKED, null, "", "", "", "");
        }
        PlanningNextAction nextAction =
                switch (decision.nextAction()) {
                    case ASK_USER -> PlanningNextAction.ASK_USER;
                    case READY_FOR_PACKET -> PlanningNextAction.READY_FOR_PACKET;
                    case CONTINUE_SYNTHESIS, BLOCK -> PlanningNextAction.BLOCKED;
                };
        return new PlanningDecisionSnapshot(
                nextAction,
                null,
                nextAction == PlanningNextAction.ASK_USER ? decision.questionText() : "",
                nextAction == PlanningNextAction.BLOCKED ? decision.blockingReason() : "",
                decision.repoGroundingState(),
                decision.confidenceSummary());
    }

    public static PlanningCanonicalDecision toCanonicalDecision(
            PlanningDecisionSnapshot snapshot,
            PlanningEvaluationDecision decision,
            String source,
            List<String> explicitAssumptions,
            String materialStateChangeFingerprint) {
        PlanningDecisionSnapshot safeSnapshot =
                snapshot != null ? snapshot : new PlanningDecisionSnapshot(PlanningNextAction.BLOCKED, null, "", "", "", "");
        PlanningCanonicalNextAction canonicalNextAction =
                switch (safeSnapshot.nextAction()) {
                    case ASK_USER -> PlanningCanonicalNextAction.ASK_USER;
                    case READY_FOR_PACKET -> PlanningCanonicalNextAction.READY_FOR_PACKET;
                    case BLOCKED -> PlanningCanonicalNextAction.BLOCK;
                };
        PlanningIntakeStage stage =
                switch (safeSnapshot.nextAction()) {
                    case ASK_USER -> PlanningIntakeStage.CLARIFYING;
                    case READY_FOR_PACKET -> PlanningIntakeStage.DRAFTING;
                    case BLOCKED -> PlanningIntakeStage.FAILED;
                };
        PlanningInteractionState interactionState =
                safeSnapshot.nextAction() == PlanningNextAction.ASK_USER
                        ? PlanningInteractionState.WAITING_FOR_TEXT_REPLY
                        : PlanningInteractionState.NONE;
        return PlanningCanonicalDecision.create(
                source != null && !source.isBlank() ? source : "planning_evaluation",
                stage,
                canonicalNextAction,
                interactionState,
                safeSnapshot.repoEvidenceStatus(),
                safeSnapshot.decisionSummary(),
                safeSnapshot.nextAction() == PlanningNextAction.READY_FOR_PACKET,
                false,
                false,
                decision != null ? decision.topGapText() : "",
                decision != null ? decision.topGapId() : "",
                safeSnapshot.nextAction() == PlanningNextAction.ASK_USER ? safeSnapshot.nextQuestion() : "",
                safeSnapshot.nextAction() == PlanningNextAction.BLOCKED ? safeSnapshot.blockingReason() : "",
                explicitAssumptions != null ? explicitAssumptions : List.of(),
                materialStateChangeFingerprint);
    }
}
