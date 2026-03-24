package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanConfidence;
import com.vinekeepers.state.planning.PlanReadinessStatus;
import com.vinekeepers.state.planning.PlanningCanonicalDecision;
import com.vinekeepers.state.planning.PlanningCanonicalNextAction;
import com.vinekeepers.state.planning.PlanningIntakeStage;
import com.vinekeepers.state.planning.PlanningInteractionState;

import java.util.List;
import java.util.Map;

/**
 * Builds, persists, and projects canonical planning decisions.
 */
public final class PlanningCanonicalDecisionSupport {

    public static final String CANONICAL_NEXT_ACTION_KEY = "planningCanonicalNextAction";
    public static final String CANONICAL_STAGE_KEY = "canonicalPlanningIntakeStage";
    public static final String CANONICAL_INTERACTION_STATE_KEY = "planningCanonicalInteractionState";
    public static final String CANONICAL_DECISION_ID_KEY = "planningCanonicalDecisionId";
    public static final String CANONICAL_DECISION_SOURCE_KEY = "planningCanonicalDecisionSource";
    public static final String CANONICAL_DECISION_VERSION_KEY = "planningCanonicalDecisionVersion";
    public static final String CANONICAL_DECISION_NORMALIZED_AT_KEY = "planningCanonicalDecisionNormalizedAt";
    public static final String CANONICAL_TOP_GAP_ID_KEY = "planningCanonicalTopGapId";
    public static final String CANONICAL_TOP_GAP_TEXT_KEY = "planningCanonicalTopGap";
    public static final String LAST_MATERIAL_CHANGE_FP_KEY = "planningLastMaterialStateChangeFingerprint";

    private PlanningCanonicalDecisionSupport() {}

    public static PlanningCanonicalDecision readFromPlanOrState(FeaturePlanState plan, Map<String, ?> state) {
        if (plan != null && plan.getPlanningCanonicalDecision() != null) {
            return plan.getPlanningCanonicalDecision();
        }
        if (state == null) {
            return PlanningCanonicalDecision.empty();
        }
        String nextAction = stringValue(state.get(CANONICAL_NEXT_ACTION_KEY));
        if (nextAction.isBlank()) {
            return PlanningCanonicalDecision.empty();
        }
        return PlanningCanonicalDecision.create(
                stringValue(state.get(CANONICAL_DECISION_SOURCE_KEY)),
                parseStage(stringValue(state.get(CANONICAL_STAGE_KEY))),
                parseNextAction(nextAction),
                parseInteractionState(stringValue(state.get(CANONICAL_INTERACTION_STATE_KEY))),
                stringValue(state.get("planningCanonicalRepoGroundingState")),
                stringValue(state.get("planningCanonicalConfidenceSummary")),
                truthy(state.get(PlanningReadinessSpread.PACKET_POSTING_ALLOWED_KEY)),
                truthy(state.get(PlanningReadinessSpread.REVIEW_ALLOWED_KEY)),
                truthy(state.get(PlanningReadinessSpread.APPROVAL_ALLOWED_KEY)),
                stringValue(state.get(CANONICAL_TOP_GAP_TEXT_KEY)),
                stringValue(state.get(CANONICAL_TOP_GAP_ID_KEY)),
                stringValue(state.get("planningClarificationQuestionText")),
                stringValue(state.get("planningCanonicalBlockingReason")),
                List.of(),
                stringValue(state.get(LAST_MATERIAL_CHANGE_FP_KEY)));
    }

    public static void projectRoutingSnapshotFromCanonical(Map<String, Object> spread, PlanningCanonicalDecision decision) {
        PlanningRoutingBridge.projectSnapshotToSpread(spread, PlanningRoutingBridge.snapshotFromCanonical(decision));
    }

    /**
     * Canonical decision fields without clarification-shaped keys; use with {@link
     * PlanningClarificationProjectionAdapter#applyFromCanonicalDecision} or evaluation overlays.
     */
    public static void projectCanonicalCoreToSpread(Map<String, Object> spread, PlanningCanonicalDecision decision) {
        if (spread == null || decision == null) {
            return;
        }
        spread.put(CANONICAL_DECISION_VERSION_KEY, String.valueOf(decision.version()));
        spread.put(CANONICAL_DECISION_ID_KEY, decision.decisionId());
        spread.put(CANONICAL_DECISION_SOURCE_KEY, decision.source());
        spread.put(CANONICAL_DECISION_NORMALIZED_AT_KEY, decision.normalizedAt());
        spread.put(CANONICAL_NEXT_ACTION_KEY, decision.nextAction().name());
        spread.put(CANONICAL_STAGE_KEY, decision.stage().name());
        spread.put(CANONICAL_INTERACTION_STATE_KEY, decision.interactionState().name());
        spread.put("planningCanonicalRepoGroundingState", decision.repoGroundingState());
        spread.put("planningCanonicalConfidenceSummary", decision.confidenceSummary());
        spread.put(PlanningReadinessSpread.PACKET_POSTING_ALLOWED_KEY, decision.packetPostingAllowed() ? "true" : "false");
        spread.put(PlanningReadinessSpread.REVIEW_ALLOWED_KEY, decision.reviewAllowed() ? "true" : "false");
        spread.put(PlanningReadinessSpread.APPROVAL_ALLOWED_KEY, decision.approvalAllowed() ? "true" : "false");
        spread.put(CANONICAL_TOP_GAP_TEXT_KEY, decision.topUnresolvedGap());
        spread.put(CANONICAL_TOP_GAP_ID_KEY, decision.topUnresolvedGapId());
        spread.put("planningCanonicalBlockingReason", decision.blockingReason());
        spread.put(LAST_MATERIAL_CHANGE_FP_KEY, decision.materialStateChangeFingerprint());
    }

    public static void projectToSpread(Map<String, Object> spread, PlanningCanonicalDecision decision) {
        projectCanonicalCoreToSpread(spread, decision);
        projectRoutingSnapshotFromCanonical(spread, decision);
        PlanningClarificationProjectionAdapter.applyFromCanonicalDecision(spread, decision);
    }

    /**
     * Post-critique canonical routing. Prefer evaluation-backed clarification projection; when critique wants another ask but
     * the evaluator returns no question, callers may recover one line from merged synthesis spread keys before falling through
     * to a non-BLOCK readiness path.
     */
    public static PlanningCanonicalDecision normalizePostCritique(
            FeaturePlanState plan,
            String readinessStatus,
            boolean wantsClarification,
            boolean autoRevisionCapped,
            String planningRepoEvidenceJson,
            String materialStateChangeFingerprint,
            String blockingReason,
            String clarificationQuestionText,
            String clarificationGapId) {
        String status = readinessStatus != null ? readinessStatus : "";
        String q = clarificationQuestionText != null ? clarificationQuestionText.trim() : "";
        String gid = clarificationGapId != null ? clarificationGapId.trim() : "";
        if (wantsClarification && !q.isBlank()) {
            return PlanningCanonicalDecision.create(
                    "post_critique",
                    PlanningIntakeStage.CLARIFYING,
                    PlanningCanonicalNextAction.ASK_USER,
                    PlanningInteractionState.WAITING_FOR_TEXT_REPLY,
                    notMaterializedFallback(plan, planningRepoEvidenceJson),
                    confidenceSummary(plan),
                    false,
                    false,
                    false,
                    q,
                    gid,
                    q,
                    "",
                    List.of(),
                    materialStateChangeFingerprint);
        }
        if (PlanReadinessStatus.BLOCKED.equals(status) || autoRevisionCapped) {
            return PlanningCanonicalDecision.create(
                    "post_critique",
                    PlanningIntakeStage.FAILED,
                    PlanningCanonicalNextAction.BLOCK,
                    PlanningInteractionState.NONE,
                    notMaterializedFallback(plan, planningRepoEvidenceJson),
                    confidenceSummary(plan),
                    false,
                    false,
                    false,
                    "",
                    "",
                    "",
                    blankToEmpty(blockingReason),
                    List.of(),
                    materialStateChangeFingerprint);
        }
        boolean reviewAllowed =
                PlanReadinessStatus.REVIEWABLE.equals(status)
                        || PlanReadinessStatus.CONDITIONALLY_READY.equals(status)
                        || PlanReadinessStatus.READY.equals(status);
        boolean approvalAllowed =
                PlanReadinessStatus.CONDITIONALLY_READY.equals(status)
                        || PlanReadinessStatus.READY.equals(status);
        PlanningIntakeStage stage = approvalAllowed ? PlanningIntakeStage.AWAITING_APPROVAL : PlanningIntakeStage.READINESS_GATE;
        return PlanningCanonicalDecision.create(
                "post_critique",
                stage,
                PlanningCanonicalNextAction.READY_FOR_PACKET,
                approvalAllowed ? PlanningInteractionState.WAITING_FOR_APPROVAL_INTERACTION : PlanningInteractionState.NONE,
                notMaterializedFallback(plan, planningRepoEvidenceJson),
                confidenceSummary(plan),
                false,
                reviewAllowed,
                approvalAllowed,
                "",
                "",
                "",
                "",
                List.of(),
                materialStateChangeFingerprint);
    }

    private static String confidenceSummary(FeaturePlanState plan) {
        if (plan == null) {
            return "";
        }
        PlanConfidence c = plan.getPlanConfidence();
        if (c == null) {
            return "";
        }
        return c.getNotes() != null ? c.getNotes() : "";
    }

    private static boolean hasMaterialChange(FeaturePlanState plan, String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return false;
        }
        if (plan == null) {
            return true;
        }
        return !fingerprint.equals(plan.getPlanningLastMaterialStateChangeFingerprint());
    }

    private static boolean truthy(Object raw) {
        if (raw == null) {
            return false;
        }
        String t = raw.toString().trim();
        return "true".equalsIgnoreCase(t) || "1".equals(t) || "yes".equalsIgnoreCase(t);
    }

    private static String stringValue(Object raw) {
        return raw != null ? raw.toString().trim() : "";
    }

    private static String blankToEmpty(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static String notMaterializedFallback(FeaturePlanState plan, String planningRepoEvidenceJson) {
        if (plan == null) {
            return "NOT_MATERIALIZED";
        }
        String workspace = plan.getRepoWorkspaceStatus() != null ? plan.getRepoWorkspaceStatus().trim() : "";
        if ("MATERIALIZED".equalsIgnoreCase(workspace) || "RESOLVED_LOCAL".equalsIgnoreCase(workspace)) {
            return planningRepoEvidenceJson != null && !planningRepoEvidenceJson.isBlank()
                    ? "INSPECTED"
                    : "MATERIALIZED_NOT_INSPECTED";
        }
        return "NOT_MATERIALIZED";
    }

    private static PlanningIntakeStage parseStage(String raw) {
        try {
            return PlanningIntakeStage.valueOf(raw);
        } catch (Exception e) {
            return PlanningIntakeStage.GATHERING_CONTEXT;
        }
    }

    private static PlanningCanonicalNextAction parseNextAction(String raw) {
        try {
            return PlanningCanonicalNextAction.valueOf(raw);
        } catch (Exception e) {
            return PlanningCanonicalNextAction.BLOCK;
        }
    }

    private static PlanningInteractionState parseInteractionState(String raw) {
        try {
            return PlanningInteractionState.valueOf(raw);
        } catch (Exception e) {
            return PlanningInteractionState.NONE;
        }
    }
}
