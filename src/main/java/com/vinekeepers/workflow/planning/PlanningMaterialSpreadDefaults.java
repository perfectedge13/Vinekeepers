package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.PlanningCanonicalNextAction;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Default spread keys for a planning-room cycle before synthesis/evaluation runs.
 */
public final class PlanningMaterialSpreadDefaults {

    private PlanningMaterialSpreadDefaults() {}

    public static Map<String, Object> newPlanningCycleBaseSpread() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planningRoomCycleError", "");
        m.put("planningCanonicalUserInputRequired", "false");
        m.put("planningClarificationChoicesJson", "[]");
        m.put("planningClarificationMetaJson", "{}");
        m.put("planningOrchestratorRoundSummary", "");
        m.put("planningPacketDepthOk", "false");
        m.put("planningPacketDepthReason", "");
        m.put("planningPacketDepthRetryRecommended", "false");
        m.put(PlanningRoutingBridge.NEXT_ACTION_KEY, PlanningNextAction.BLOCKED.name());
        m.put(PlanningRoutingBridge.LAST_CONFIDENCE_KEY, "");
        m.put(PlanningRoutingBridge.NEXT_QUESTION_KEY, "");
        m.put(PlanningRoutingBridge.BLOCKING_REASON_KEY, "");
        m.put(PlanningRoutingBridge.REPO_EVIDENCE_STATUS_KEY, "");
        m.put(PlanningRoutingBridge.DECISION_SUMMARY_KEY, "");
        m.put(PlanningReadinessSpread.PACKET_POSTING_ALLOWED_KEY, "false");
        m.put(PlanningReadinessSpread.REVIEW_ALLOWED_KEY, "false");
        m.put(PlanningReadinessSpread.APPROVAL_ALLOWED_KEY, "false");
        m.put("planningAssumptionsUsed", "0");
        m.put("planningRolePassLastError", "");
        m.put("planningPassInterrupted", "false");
        m.put("planningStructuredPassParseFailed", "false");
        m.put("planningClarificationUseStructuredChoices", "false");
        m.put("planningClarificationQuestionText", "");
        m.put("planningClarificationOrchestratorPrompt", "");
        m.put("planningCycleProgressSummary", "");
        m.put("planningProgressPostWorthy", "true");
        m.put("planningProgressPostFingerprint", "");
        m.put("planningClarificationStuck", "false");
        m.put("planningClarificationStuckHint", "");
        m.put("planningClarificationRepeatCount", "0");
        m.put("planningClarificationLedgerItemId", "");
        m.put("planningJustMergedClarification", "false");
        m.put("planningHardClarificationBlockReason", "");
        m.put("planningSelectiveRerunActive", "false");
        m.put("planningSelectiveRerunNote", "");
        m.put("workflowUnresolvedHasOpen", "false");
        m.put("planningCycleRolePassSummary", "");
        m.put("planningCycleSynthesisLlmNote", "");
        m.put("planningCycleUserVisibleFailure", "");
        m.put("planningLlmError", "");
        m.put("planningLlmSkipReason", "");
        m.put(PlanningReadinessSpread.HUMAN_READINESS_ACKNOWLEDGED_KEY, "false");
        m.put("planningReviewReady", "false");
        m.put("reviewReady", "false");
        m.put("planningApprovalReady", "false");
        m.put("approvalReady", "false");
        m.put("planningReviewReadyReason", "");
        m.put("reviewReadyReason", "");
        m.put("planningApprovalReadyReason", "");
        m.put("approvalReadyReason", "");
        m.put("deliberationPhase", "");
        m.put("planningRolePassOrderResolved", "");
        m.put("planningDirtyPassesJson", "[]");
        m.put("planningDirtyPassCount", "0");
        m.put("userCopyProgressLine", "");
        m.put("userCopyCoordinatorProgress", "");
        m.put(PlanningCanonicalDecisionSupport.CANONICAL_NEXT_ACTION_KEY, PlanningCanonicalNextAction.BLOCK.name());
        m.put(PlanningMaterialSpreadKeys.NOTICE_MARKDOWN_KEY, "");
        m.put(PlanningMaterialSpreadKeys.BASELINE_REPO_HASH_KEY, "");
        m.put(PlanningMaterialSpreadKeys.BASELINE_ASSUMPTION_COUNT_KEY, "");
        m.put(PlanningMaterialSpreadKeys.BASELINE_CRITIQUE_BLOCKING_KEY, "");
        m.put(PlanningMaterialSpreadKeys.BASELINE_DRAFT_FP_KEY, "");
        m.put(PlanningMaterialSpreadKeys.LAST_REVISION_SITUATION_KEY, "");
        m.put(PlanningMaterialSpreadKeys.MATERIAL_ACTION_KEY, PlanningSynthesisAction.BLOCK.name());
        m.put(PlanningMaterialSpreadKeys.MATERIAL_NOTICE_MARKDOWN_KEY, "");
        m.put(PlanningMaterialSpreadKeys.MATERIAL_FORCE_USER_INPUT_KEY, "false");
        m.put(PlanningMaterialSpreadKeys.MATERIAL_USER_INPUT_REQUIRED_KEY, "false");
        m.put(PlanningMaterialSpreadKeys.MATERIAL_READY_FOR_PACKET_KEY, "false");
        m.put(PlanningMaterialSpreadKeys.MATERIAL_REVISION_NEEDED_KEY, "false");
        m.put(PlanningMaterialSpreadKeys.MATERIAL_PACKET_POSTING_ALLOWED_KEY, "false");
        return m;
    }
}
