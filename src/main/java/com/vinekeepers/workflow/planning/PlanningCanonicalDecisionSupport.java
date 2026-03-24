package com.vinekeepers.workflow.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final ObjectMapper JSON = new ObjectMapper();

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

    public static void projectToSpread(Map<String, Object> spread, PlanningCanonicalDecision decision) {
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
        if (decision.nextAction() == PlanningCanonicalNextAction.ASK_ONE_QUESTION) {
            spread.put("planningClarificationQuestionText", decision.questionText());
        } else {
            spread.put("planningClarificationQuestionText", "");
            spread.put("planningClarificationChoicesJson", "[]");
            spread.put("planningClarificationMetaJson", "{}");
            spread.put("planningClarificationOrchestratorPrompt", "");
            spread.put("planningClarificationUseStructuredChoices", "false");
        }
        spread.put(LAST_MATERIAL_CHANGE_FP_KEY, decision.materialStateChangeFingerprint());
    }

    public static String repoGroundingState(FeaturePlanState plan, String planningRepoEvidenceJson) {
        if (plan == null) {
            return "NOT_MATERIALIZED";
        }
        String workspace = plan.getRepoWorkspaceStatus() != null ? plan.getRepoWorkspaceStatus().trim() : "";
        boolean materialized = "MATERIALIZED".equalsIgnoreCase(workspace) || "RESOLVED_LOCAL".equalsIgnoreCase(workspace);
        if (!materialized) {
            return "NOT_MATERIALIZED";
        }
        if (repoEvidenceScore(planningRepoEvidenceJson) >= 0.55d) {
            return "INSPECTED";
        }
        return "MATERIALIZED_NOT_INSPECTED";
    }

    /**
     * Live {@code canonical_v1}: {@link PlanningCanonicalNextAction#ASK_ONE_QUESTION} is authorized only when the coordinator
     * engine has an askable gap ({@code canonicalGapAuthorizesAsk}); otherwise {@link #normalizePostDraft} applies from
     * {@link PlanningMaterialRoutingOutcome} (material pacing — not clarification policy). {@code BLOCK} from material
     * pacing always wins over an ask.
     */
    public static PlanningCanonicalDecision normalizePostDraftCanonicalV1(
            FeaturePlanState plan,
            PlanningMaterialRoutingOutcome material,
            boolean canonicalGapAuthorizesAsk,
            ClarificationProjection ranked,
            String planningRepoEvidenceJson,
            String topGapId,
            String materialStateChangeFingerprint) {
        if (material != null
                && material.action() == PlanningPostDraftAction.BLOCK) {
            return normalizePostDraft(
                    plan, material, ranked, planningRepoEvidenceJson, topGapId, materialStateChangeFingerprint);
        }
        if (canonicalGapAuthorizesAsk) {
            String questionText =
                    ranked != null && ranked.questionText() != null ? ranked.questionText().trim() : "";
            FeaturePlanState effectivePlan = plan;
            return PlanningCanonicalDecision.create(
                    "post_draft",
                    PlanningIntakeStage.CLARIFYING,
                    PlanningCanonicalNextAction.ASK_ONE_QUESTION,
                    PlanningInteractionState.WAITING_FOR_TEXT_REPLY,
                    repoGroundingState(effectivePlan, planningRepoEvidenceJson),
                    confidenceSummary(effectivePlan),
                    false,
                    false,
                    false,
                    topGapText(ranked, effectivePlan),
                    topGapId,
                    questionText,
                    "",
                    effectivePlan != null
                            ? effectivePlan.getAssumptions().stream()
                                    .map(a -> a.getStatement())
                                    .filter(s -> s != null && !s.isBlank())
                                    .toList()
                            : List.of(),
                    materialStateChangeFingerprint);
        }
        return normalizePostDraft(
                plan, material, ranked, planningRepoEvidenceJson, topGapId, materialStateChangeFingerprint);
    }

    public static PlanningCanonicalDecision normalizePostDraft(
            FeaturePlanState plan,
            PlanningMaterialRoutingOutcome material,
            ClarificationProjection ranked,
            String planningRepoEvidenceJson,
            String topGapId,
            String materialStateChangeFingerprint) {
        FeaturePlanState effectivePlan = plan;
        PlanningCanonicalNextAction nextAction = PlanningCanonicalNextAction.BLOCK;
        PlanningIntakeStage stage = PlanningIntakeStage.FAILED;
        PlanningInteractionState interaction = PlanningInteractionState.NONE;
        boolean packetPostingAllowed = false;
        boolean reviewAllowed = false;
        boolean approvalAllowed = false;
        String questionText = ranked != null && ranked.questionText() != null ? ranked.questionText().trim() : "";
        String blockingReason = material != null ? blankToEmpty(material.noticeMarkdown()) : "";
        if (material != null) {
            switch (material.action()) {
                case ASK_ONE_QUESTION -> {
                    nextAction = PlanningCanonicalNextAction.ASK_ONE_QUESTION;
                    stage = PlanningIntakeStage.CLARIFYING;
                    interaction = PlanningInteractionState.WAITING_FOR_TEXT_REPLY;
                    packetPostingAllowed = false;
                }
                case POST_PACKET -> {
                    nextAction = PlanningCanonicalNextAction.POST_PACKET;
                    stage = PlanningIntakeStage.DRAFTING;
                    packetPostingAllowed = true;
                }
                case AUTONOMOUS_REDRAFT -> {
                    nextAction = PlanningCanonicalNextAction.AUTONOMOUS_REDRAFT;
                    stage = PlanningIntakeStage.DRAFTING;
                }
                case ASSUME_AND_CONTINUE -> {
                    boolean canPost = material.readyToPostPacket();
                    boolean changed = hasMaterialChange(plan, materialStateChangeFingerprint);
                    if (canPost && changed) {
                        nextAction = PlanningCanonicalNextAction.POST_PACKET;
                        stage = PlanningIntakeStage.DRAFTING;
                        packetPostingAllowed = true;
                    } else if (changed) {
                        nextAction = PlanningCanonicalNextAction.AUTONOMOUS_REDRAFT;
                        stage = PlanningIntakeStage.DRAFTING;
                    } else {
                        nextAction = PlanningCanonicalNextAction.BLOCK;
                        stage = PlanningIntakeStage.FAILED;
                        blockingReason = "No legal canonical action remained after assumption-handling normalization.";
                    }
                }
                case BLOCK -> {
                    nextAction = PlanningCanonicalNextAction.BLOCK;
                    stage = PlanningIntakeStage.FAILED;
                }
            }
        }
        return PlanningCanonicalDecision.create(
                "post_draft",
                stage,
                nextAction,
                interaction,
                repoGroundingState(effectivePlan, planningRepoEvidenceJson),
                confidenceSummary(effectivePlan),
                packetPostingAllowed,
                reviewAllowed,
                approvalAllowed,
                topGapText(ranked, effectivePlan),
                topGapId,
                questionText,
                blockingReason,
                effectivePlan != null ? effectivePlan.getAssumptions().stream().map(a -> a.getStatement()).filter(s -> s != null && !s.isBlank()).toList() : List.of(),
                materialStateChangeFingerprint);
    }

    public static PlanningCanonicalDecision normalizePostCritique(
            FeaturePlanState plan,
            String readinessStatus,
            boolean wantsClarification,
            boolean autoRevisionCapped,
            String planningRepoEvidenceJson,
            String materialStateChangeFingerprint,
            String blockingReason) {
        String status = readinessStatus != null ? readinessStatus : "";
        if (wantsClarification) {
            return PlanningCanonicalDecision.create(
                    "post_critique",
                    PlanningIntakeStage.CLARIFYING,
                    PlanningCanonicalNextAction.ASK_ONE_QUESTION,
                    PlanningInteractionState.WAITING_FOR_TEXT_REPLY,
                    repoGroundingState(plan, planningRepoEvidenceJson),
                    confidenceSummary(plan),
                    false,
                    false,
                    false,
                    firstUnresolvedQuestion(plan),
                    "",
                    firstUnresolvedQuestion(plan),
                    "",
                    List.of(),
                    materialStateChangeFingerprint);
        }
        if (PlanReadinessStatus.NOT_READY.equals(status) && !autoRevisionCapped && hasMaterialChange(plan, materialStateChangeFingerprint)) {
            return PlanningCanonicalDecision.create(
                    "post_critique",
                    PlanningIntakeStage.DRAFTING,
                    PlanningCanonicalNextAction.AUTONOMOUS_REDRAFT,
                    PlanningInteractionState.NONE,
                    repoGroundingState(plan, planningRepoEvidenceJson),
                    confidenceSummary(plan),
                    false,
                    false,
                    false,
                    firstUnresolvedQuestion(plan),
                    "",
                    "",
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
                    repoGroundingState(plan, planningRepoEvidenceJson),
                    confidenceSummary(plan),
                    false,
                    false,
                    false,
                    firstUnresolvedQuestion(plan),
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
                PlanningCanonicalNextAction.POST_PACKET,
                approvalAllowed ? PlanningInteractionState.WAITING_FOR_APPROVAL_INTERACTION : PlanningInteractionState.NONE,
                repoGroundingState(plan, planningRepoEvidenceJson),
                confidenceSummary(plan),
                false,
                reviewAllowed,
                approvalAllowed,
                firstUnresolvedQuestion(plan),
                "",
                "",
                "",
                List.of(),
                materialStateChangeFingerprint);
    }

    private static String firstUnresolvedQuestion(FeaturePlanState plan) {
        if (plan == null || plan.getUnresolvedQuestions().isEmpty()) {
            return "";
        }
        return plan.getUnresolvedQuestions().getFirst();
    }

    private static String topGapText(ClarificationProjection ranked, FeaturePlanState plan) {
        if (ranked != null && ranked.questionText() != null && !ranked.questionText().isBlank()) {
            return ranked.questionText().trim();
        }
        return firstUnresolvedQuestion(plan);
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

    private static double repoEvidenceScore(String planningRepoEvidenceJson) {
        if (planningRepoEvidenceJson == null || planningRepoEvidenceJson.isBlank()) {
            return -1.0d;
        }
        try {
            JsonNode n = JSON.readTree(planningRepoEvidenceJson.trim());
            return n.path("repoGroundingScore").asDouble(-1.0d);
        } catch (Exception e) {
            return -1.0d;
        }
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
