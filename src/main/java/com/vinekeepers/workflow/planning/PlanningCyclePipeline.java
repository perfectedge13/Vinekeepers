package com.vinekeepers.workflow.planning;

import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanningCanonicalDecision;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.PlanningCanonicalNextAction;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.workflow.planreview.PlanningUserFacingCopy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Planning-room orchestration only: load state, gate on workspace preflight, run silent synthesis, evaluate once,
 * persist the canonical decision, and project the minimal workflow-facing result.
 */
public final class PlanningCyclePipeline {

    private static final int MAX_BOT_INNER_ROUNDS = 3;

    /** Spread flag {@code "true"} when an OpenAI role pass returned an interrupt-style error (e.g. {@code ERROR: interrupted}). */
    public static final String PLANNING_PASS_INTERRUPTED_KEY = "planningPassInterrupted";

    /**
     * Spread flag {@code "true"} when a structured coordinator pass (Architect/Auditor/Scribe) returned output that failed
     * JSON parse (after optional repair), so readiness/posting must not claim a solid structured draft.
     */
    public static final String PLANNING_STRUCTURED_PASS_PARSE_FAILED_KEY = "planningStructuredPassParseFailed";

    public static final String PARTIAL_LAST_ROLE_SUMMARY_KEY = "planningPartialLastRoleRoundSummary";
    public static final String PARTIAL_LAST_SYNTH_KEY = "planningPartialLastSynthLlmLine";
    public static final String PARTIAL_DEPTH_OK_KEY = "planningPartialDepthOk";
    public static final String PARTIAL_DEPTH_REASON_KEY = "planningPartialDepthReason";

    public static final String PLANNING_CLARIFICATION_CONFIDENCE_SCORE_KEY =
            PlanningClarificationProjectionAdapter.CLARIFICATION_CONFIDENCE_SCORE_KEY;
    public static final String PLANNING_CLARIFICATION_CONFIDENCE_HIGH_KEY =
            PlanningClarificationProjectionAdapter.CLARIFICATION_CONFIDENCE_HIGH_KEY;

    private record LoadedCycle(
            String contextId, FeaturePlanState plan, WorkProfileDefinition profile, Map<String, Object> work) {}

    private record EvaluationLedgerPhase(
            PlanningEvaluationDecision decision,
            PlanningDecisionSnapshot snapshot,
            FeaturePlanState plan,
            ClarificationProjection projection,
            PlanningDeliberationLedgerSync.UpsertResult upsert,
            boolean structuredParseFailed) {}

    private final FeaturePlanStateStore planStateStore;
    private final WorkProfileRegistry workProfileRegistry;
    private final SilentPlanningSynthesisService silentSynthesis;
    private final PlanningEvaluationService planningEvaluationService;

    public PlanningCyclePipeline(
            OpenAiChatClient openAiChatClient,
            FeaturePlanStateStore planStateStore,
            WorkProfileRegistry workProfileRegistry) {
        this.planStateStore = planStateStore;
        this.workProfileRegistry = workProfileRegistry;
        this.silentSynthesis = new SilentPlanningSynthesisService(openAiChatClient, planStateStore, workProfileRegistry);
        this.planningEvaluationService = new PlanningEvaluationService(openAiChatClient);
    }

    public Map<String, Object> execute(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = baseSpread();
        LoadedCycle ctx = loadCycleOrAbort(event, state, bind, spread);
        if (ctx == null) {
            PlanningProgressProjection.applyProgressFingerprint(state, spread);
            return spread;
        }
        String contextId = ctx.contextId();
        WorkProfileDefinition profile = ctx.profile();
        Map<String, Object> work = ctx.work();
        FeaturePlanState plan = ctx.plan();

        PlanningWorkspacePreflightService.PreflightResult workspacePre = PlanningWorkspacePreflightService.evaluate(plan);
        spread.put("planningWorkspacePreflightStatus", workspacePre.status().name());
        if (workspacePre.blocked()) {
            spread.put("planningWorkspaceUserInputRequired", "true");
            spread.put("planningWorkspaceBlockerPrompt", workspacePre.userPrompt());
            putPlanningRoomCycleError(spread, "WORKSPACE_BLOCKED");
            PlanningProgressProjection.applyProgressFingerprint(state, spread);
            return spread;
        }

        if (!profile.getCoordinatorClarification().isCanonicalV1()) {
            putPlanningRoomCycleError(spread, "PLANNING_REQUIRES_CANONICAL_V1");
            PlanningProgressProjection.applyProgressFingerprint(state, spread);
            return spread;
        }

        int cycleIteration = parseInt(getString(state, "planningRoomCycleIteration"), 0);
        cycleIteration++;
        spread.put("planningRoomCycleIteration", String.valueOf(cycleIteration));

        boolean selective = selectiveRerunAfterClarificationEnabled(state, bind);
        boolean justMerged = "true".equalsIgnoreCase(getString(state, "planningJustMergedClarification"));
        boolean skipExpansion = selective && justMerged;
        SilentPlanningSynthesisService.SilentPlanningSynthesisResult synthesis =
                silentSynthesis.runSilentSynthesisPhase(
                        event,
                        contextId,
                        profile,
                        work,
                        spread,
                        bind,
                        plan,
                        skipExpansion,
                        "",
                        MAX_BOT_INNER_ROUNDS);
        return finalizeEvaluation(
                event,
                state,
                spread,
                contextId,
                planStateStore.getByContextId(contextId).orElse(synthesis.plan()),
                profile,
                cycleIteration,
                synthesis.depthOk(),
                synthesis.depthReason(),
                synthesis.lastRoleRoundSummary(),
                synthesis.lastSynthLlmLine());
    }

    /**
     * Workflow-facing silent synthesis operation: workspace preflight, selective rerun decision, and drafting internals.
     * Evaluation and deterministic branching stay in the next step.
     */
    public Map<String, Object> runSilentSynthesisOnly(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = baseSpread();
        LoadedCycle ctx = loadCycleOrAbort(event, state, bind, spread);
        if (ctx == null) {
            PlanningProgressProjection.applyProgressFingerprint(state, spread);
            return spread;
        }
        String contextId = ctx.contextId();
        WorkProfileDefinition profile = ctx.profile();
        Map<String, Object> work = ctx.work();
        FeaturePlanState plan = ctx.plan();

        PlanningWorkspacePreflightService.PreflightResult workspacePre = PlanningWorkspacePreflightService.evaluate(plan);
        spread.put("planningWorkspacePreflightStatus", workspacePre.status().name());
        if (workspacePre.blocked()) {
            spread.put("planningWorkspaceUserInputRequired", "true");
            spread.put("planningWorkspaceBlockerPrompt", workspacePre.userPrompt());
            putPlanningRoomCycleError(spread, "WORKSPACE_BLOCKED");
            PlanningProgressProjection.applyProgressFingerprint(state, spread);
            return spread;
        }

        if (!profile.getCoordinatorClarification().isCanonicalV1()) {
            putPlanningRoomCycleError(spread, "PLANNING_REQUIRES_CANONICAL_V1");
            PlanningProgressProjection.applyProgressFingerprint(state, spread);
            return spread;
        }

        int cycleIteration = parseInt(getString(state, "planningRoomCycleIteration"), 0) + 1;
        spread.put("planningRoomCycleIteration", String.valueOf(cycleIteration));

        boolean selective = selectiveRerunAfterClarificationEnabled(state, bind);
        boolean justMerged = "true".equalsIgnoreCase(getString(state, "planningJustMergedClarification"));
        boolean skipExpansion = selective && justMerged;
        SilentPlanningSynthesisService.SilentPlanningSynthesisResult synthesis =
                silentSynthesis.runSilentSynthesisPhase(
                        event,
                        contextId,
                        profile,
                        work,
                        spread,
                        bind,
                        plan,
                        skipExpansion,
                        "",
                        MAX_BOT_INNER_ROUNDS);
        spread.put(PARTIAL_LAST_ROLE_SUMMARY_KEY, synthesis.lastRoleRoundSummary());
        spread.put(PARTIAL_LAST_SYNTH_KEY, synthesis.lastSynthLlmLine());
        spread.put(PARTIAL_DEPTH_OK_KEY, synthesis.depthOk() ? "true" : "false");
        spread.put(PARTIAL_DEPTH_REASON_KEY, synthesis.depthReason() != null ? synthesis.depthReason() : "");
        PlanningProgressProjection.applyCycleProgressSummaryAfterSilentSynthesis(spread, cycleIteration);
        PlanningProgressProjection.applyUserCopyAndProgressLog(state, spread);
        PlanningProgressProjection.applyProgressFingerprint(state, spread);
        return spread;
    }

    /** Deferred evaluation step after silent synthesis. */
    public Map<String, Object> runEvaluationOnly(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = baseSpread();
        LoadedCycle ctx = loadCycleOrAbort(event, state, bind, spread);
        if (ctx == null) {
            PlanningProgressProjection.applyProgressFingerprint(state, spread);
            return spread;
        }
        WorkProfileDefinition profileFin0 = ctx.profile();
        if (!profileFin0.getCoordinatorClarification().isCanonicalV1()) {
            putPlanningRoomCycleError(spread, "PLANNING_REQUIRES_CANONICAL_V1");
            PlanningProgressProjection.applyProgressFingerprint(state, spread);
            return spread;
        }
        boolean depthOk = "true".equalsIgnoreCase(getString(state, PARTIAL_DEPTH_OK_KEY));
        String depthReason = firstNonBlank(getString(state, PARTIAL_DEPTH_REASON_KEY), "");
        String lastRoleRoundSummary = firstNonBlank(getString(state, PARTIAL_LAST_ROLE_SUMMARY_KEY), "");
        String lastSynthLlmLine = firstNonBlank(getString(state, PARTIAL_LAST_SYNTH_KEY), "");
        return finalizeEvaluation(
                event,
                state,
                spread,
                ctx.contextId(),
                planStateStore.getByContextId(ctx.contextId()).orElse(ctx.plan()),
                ctx.profile(),
                parseInt(getString(state, "planningRoomCycleIteration"), 0),
                depthOk,
                depthReason,
                lastRoleRoundSummary,
                lastSynthLlmLine);
    }

    private Map<String, Object> finalizeEvaluation(
            Event event,
            Map<String, Object> state,
            Map<String, Object> spread,
            String contextId,
            FeaturePlanState plan,
            WorkProfileDefinition profile,
            int cycleIteration,
            boolean depthOk,
            String depthReason,
            String lastRoleRoundSummary,
            String lastSynthLlmLine) {
        applySynthesisDepthFootersToSpread(
                spread, state, depthOk, depthReason, lastRoleRoundSummary, lastSynthLlmLine);
        EvaluationLedgerPhase phase =
                evaluatePersistAndSyncLedger(
                        event, state, spread, contextId, plan, profile, cycleIteration, depthOk, depthReason);
        PlanningEvaluationDecision decision = phase.decision();
        PlanningDecisionSnapshot snapshot = phase.snapshot();
        plan = phase.plan();
        ClarificationProjection projection = phase.projection();
        PlanningDeliberationLedgerSync.UpsertResult upsert = phase.upsert();
        PlanningRoutingBridge.projectSnapshotToSpread(spread, snapshot);

        PlanningClarificationProjectionAdapter.applyPreCanonicalEvaluationClarification(
                spread, upsert, projection, snapshot);

        Map<String, Object> signal = new LinkedHashMap<>();
        if (state != null) {
            signal.putAll(state);
        }
        signal.putAll(spread);
        String materialFingerprint = PlanningMaterialFingerprint.materialStateChangeFingerprint(signal, plan);
        PlanningCanonicalDecision canonical =
                PlanningRoutingBridge.toCanonicalDecision(
                        snapshot,
                        decision,
                        "planning_evaluation",
                        plan != null
                                ? plan.getAssumptions().stream()
                                        .map(a -> a.getStatement())
                                        .filter(s -> s != null && !s.isBlank())
                                        .toList()
                                : List.of(),
                        materialFingerprint);
        if (contextId != null && !contextId.isBlank() && plan != null) {
            String askedDecisionId =
                    canonical.nextAction() == PlanningCanonicalNextAction.ASK_USER ? canonical.decisionId() : "";
            FeaturePlanState updatedPlan =
                    plan.withPlanningCanonicalDecision(canonical, materialFingerprint, askedDecisionId)
                            .withPlanningIntakeStage(canonical.stage(), java.time.Instant.now());
            planStateStore.update(updatedPlan);
            plan = planStateStore.getByContextId(contextId).orElse(updatedPlan);
        }

        applyMaterialReadinessCanonicalAndPersistence(
                spread,
                signal,
                plan,
                canonical,
                materialFingerprint,
                depthOk,
                depthReason,
                phase.structuredParseFailed(),
                upsert,
                projection);

        PlanningPipelineProjectionAdapter.applyPostCanonicalEvaluationTail(
                spread, state, decision, canonical, planStateStore, contextId, cycleIteration);
        return spread;
    }

    private void applySynthesisDepthFootersToSpread(
            Map<String, Object> spread,
            Map<String, Object> state,
            boolean depthOk,
            String depthReason,
            String lastRoleRoundSummary,
            String lastSynthLlmLine) {
        spread.put("planningCycleRolePassSummary", lastRoleRoundSummary != null ? lastRoleRoundSummary : "");
        spread.put("planningCycleSynthesisLlmNote", lastSynthLlmLine != null ? lastSynthLlmLine : "");
        spread.put("planningPacketDepthOk", depthOk ? "true" : "false");
        spread.put("planningPacketDepthReason", depthReason != null ? depthReason : "");
        spread.put("planningPacketDepthRetryRecommended", depthOk ? "false" : "true");

        boolean structuredParseFailed =
                "true".equalsIgnoreCase(getString(state, PLANNING_STRUCTURED_PASS_PARSE_FAILED_KEY))
                        || "true".equalsIgnoreCase(getString(spread, PLANNING_STRUCTURED_PASS_PARSE_FAILED_KEY));
        spread.put(PLANNING_STRUCTURED_PASS_PARSE_FAILED_KEY, structuredParseFailed ? "true" : "false");
    }

    private EvaluationLedgerPhase evaluatePersistAndSyncLedger(
            Event event,
            Map<String, Object> state,
            Map<String, Object> spread,
            String contextId,
            FeaturePlanState plan,
            WorkProfileDefinition profile,
            int cycleIteration,
            boolean depthOk,
            String depthReason) {
        boolean structuredParseFailed =
                "true".equalsIgnoreCase(getString(state, PLANNING_STRUCTURED_PASS_PARSE_FAILED_KEY))
                        || "true".equalsIgnoreCase(getString(spread, PLANNING_STRUCTURED_PASS_PARSE_FAILED_KEY));
        PlanningEvaluationService.EvaluationContext evaluationContext =
                new PlanningEvaluationService.EvaluationContext(
                        "planning_clarification",
                        repoGroundingSummary(plan, state),
                        getString(state, "planningRepoEvidenceJson"),
                        depthOk,
                        depthReason != null ? depthReason : "",
                        structuredParseFailed,
                        plan != null ? plan.getClarificationTurnsCompleted() : 0);
        PlanningEvaluationDecision decision =
                planningEvaluationService.evaluate(event, state, plan, profile, evaluationContext);
        if (!decision.success()) {
            putPlanningRoomCycleError(spread, decision.machineError());
        }

        if (decision.success() && plan != null) {
            plan = planningEvaluationService.applyEvaluationDeltas(plan, decision);
            plan = plan.withPlanningConfidenceBreakdownJson(
                    planningEvaluationService.toConfidenceBreakdown(plan, decision, evaluationContext).toJson());
            planStateStore.update(plan);
            plan = planStateStore.getByContextId(contextId).orElse(plan);
        }

        ClarificationProjection projection = ClarificationProjection.fromSelection(CanonicalClarificationSelection.none());
        PlanningDecisionSnapshot snapshot = PlanningDecisionNormalizer.fromEvaluation(decision);
        UnresolvedItemLedger ledger = UnresolvedItemLedger.readFrom(state);
        PlanningDeliberationLedgerSync.UpsertResult upsert =
                PlanningDeliberationLedgerSync.upsertOpenQuestion(ledger, projection);
        if (snapshot.nextAction() == PlanningNextAction.ASK_USER) {
            CanonicalPlanningGap askGap = decision.chosenAskGap();
            projection =
                    CanonicalClarificationSpreadBuilder.projectCanonicalPlanningGap(
                            ledger,
                            profile,
                            profile.getCoordinatorClarification(),
                            askGap,
                            decision.canonicalQuestionText(),
                            List.of(),
                            QuestionMode.OPEN);
            int nextAskCount = PlanningGapAskCounts.countForGap(plan.getPlanningGapAskCountsJson(), askGap.gapId()) + 1;
            upsert =
                    PlanningDeliberationLedgerSync.upsertOpenQuestionForCanonicalGap(
                            ledger,
                            projection,
                            askGap.gapId(),
                            askGap.blocking(),
                            nextAskCount,
                            PlanningDeliberationLedgerSync.escalationLevelForPriorAskCount(nextAskCount - 1));
            plan = PlanningDeliberationLedgerSync.applyCanonicalGapAskSurfaced(plan, planStateStore, contextId, askGap, cycleIteration);
        } else {
            ledger = PlanningDeliberationLedgerSync.reconcileCanonicalOpenGaps(ledger, java.util.Set.of());
            upsert = PlanningDeliberationLedgerSync.upsertOpenQuestion(ledger, projection);
        }
        return new EvaluationLedgerPhase(decision, snapshot, plan, projection, upsert, structuredParseFailed);
    }

    private void applyMaterialReadinessCanonicalAndPersistence(
            Map<String, Object> spread,
            Map<String, Object> signal,
            FeaturePlanState plan,
            PlanningCanonicalDecision canonical,
            String materialFingerprint,
            boolean depthOk,
            String depthReason,
            boolean structuredParseFailed,
            PlanningDeliberationLedgerSync.UpsertResult upsert,
            ClarificationProjection projection) {
        PlanningMaterialCyclePacing.readinessResultAlignedWithCanonical(canonical, spread);
        PlanningReadinessSpread.applyCycleReadiness(spread);
        PlanningCanonicalDecisionSupport.projectCanonicalCoreToSpread(spread, canonical);
        PlanningClarificationProjectionAdapter.applyFromCanonicalDecision(spread, canonical);
        if (canonical.nextAction() == PlanningCanonicalNextAction.ASK_USER) {
            PlanningClarificationProjectionAdapter.applyStructuredClarificationOverlayForAskUser(spread, projection);
        }
        String situation =
                PlanningMaterialFingerprint.revisionSituationFingerprint(
                        depthOk,
                        structuredParseFailed,
                        depthReason != null ? depthReason : "",
                        canonical.nextAction() == PlanningCanonicalNextAction.ASK_USER,
                        canonical.nextAction() == PlanningCanonicalNextAction.READY_FOR_PACKET,
                        upsert.ledger());
        PlanningMaterialCyclePacing.writePersistenceKeys(
                spread,
                signal,
                plan,
                canonical.nextAction() == PlanningCanonicalNextAction.ASK_USER,
                situation);
        spread.put(PlanningCanonicalDecisionSupport.LAST_MATERIAL_CHANGE_FP_KEY, materialFingerprint);
    }

    private static void putPlanningRoomCycleError(Map<String, Object> spread, String machine) {
        String m = machine != null ? machine : "";
        spread.put("planningRoomCycleError", m);
        spread.put("planningRoomCycleErrorUserMessage", PlanningUserFacingCopy.humanizePlanningRoomCycleErrorLine(m));
    }

    private LoadedCycle loadCycleOrAbort(
            Event event, Map<String, Object> state, Map<String, Object> bind, Map<String, Object> spread) {
        if (planStateStore == null || workProfileRegistry == null) {
            putPlanningRoomCycleError(spread, "MISSING_DEPS");
            return null;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            putPlanningRoomCycleError(spread, "NO_CONTEXT");
            return null;
        }
        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            putPlanningRoomCycleError(spread, "NO_PLAN");
            return null;
        }
        String profileId = plan.getProfileId();
        WorkProfileDefinition profile =
                profileId != null && !profileId.isBlank()
                        ? workProfileRegistry.get(profileId).orElse(null)
                        : null;
        if (profile == null || profile.findSection("request_exploration", "analysis").isEmpty()) {
            putPlanningRoomCycleError(spread, "PROFILE_NOT_V2");
            return null;
        }
        Map<String, Object> work = new LinkedHashMap<>();
        if (state != null) {
            work.putAll(state);
        }
        return new LoadedCycle(contextId, plan, profile, work);
    }

    private static Map<String, Object> baseSpread() {
        Map<String, Object> spread = PlanningMaterialSpreadDefaults.newPlanningCycleBaseSpread();
        PlanningRoutingBridge.clearLiveRoutingKeys(spread);
        return spread;
    }

    private static int parseInt(String s, int dflt) {
        if (s == null || s.isBlank()) {
            return dflt;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    /** Default true: after a merged clarification, skip expansion and run a single inner critique round. */
    private static boolean selectiveRerunAfterClarificationEnabled(Map<String, Object> state, Map<String, Object> bind) {
        String v =
                firstNonBlank(
                        getString(bind, "planningSelectiveRerunAfterClarification"),
                        getString(state, "planningSelectiveRerunAfterClarification"));
        if (v == null || v.isBlank()) {
            return true;
        }
        return !"false".equalsIgnoreCase(v.trim());
    }

    private static String repoGroundingSummary(FeaturePlanState plan, Map<String, Object> state) {
        String workspace = plan != null ? firstNonBlank(plan.getRepoWorkspaceStatus(), "") : "";
        String repoJson = getString(state, "planningRepoEvidenceJson");
        if (repoJson != null && !repoJson.isBlank()) {
            return "workspaceStatus=" + workspace + "; repoEvidence=" + repoJson;
        }
        return "workspaceStatus=" + workspace;
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }
}
