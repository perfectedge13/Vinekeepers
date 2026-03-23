package com.vinekeepers.workflow.planning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.CoordinatorClarificationGapRule;
import com.vinekeepers.profile.CoordinatorClarificationSettings;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.PlanAssumption;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.workflow.ProgressDedupeHelper;
import com.vinekeepers.state.workflow.ProgressEventLog;
import com.vinekeepers.state.workflow.UnresolvedItem;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.state.workflow.UnresolvedItemStatus;
import com.vinekeepers.workflow.actions.BuildRequestExplorationAction;
import com.vinekeepers.workflow.actions.ExpandPlanningDraftsAction;
import com.vinekeepers.workflow.actions.RunLlmPlanningSynthesisAction;
import com.vinekeepers.workflow.actions.RunRequestExpansionLlmAction;
import com.vinekeepers.workflow.actions.SynthesizePreCritiqueArtifactsAction;
import com.vinekeepers.workflow.deliberation.DeliberationEngine;
import com.vinekeepers.state.planning.ClarificationResolutionDecision;
import com.vinekeepers.state.planning.PlanningFailureCategory;
import com.vinekeepers.state.planning.PlanningIntakeStage;
import com.vinekeepers.workflow.planning.ClarificationEngineAssessor.AssessedGap;
import com.vinekeepers.workflow.planning.PlanningQuestionRankingPolicy.RankedClarification;
import com.vinekeepers.workflow.planning.PlanningRolePassRunner.RolePassResult;
import com.vinekeepers.workflow.planreview.PlanningArtifactTexts;
import com.vinekeepers.workflow.planreview.PlanningPacketDepthEvaluator;
import com.vinekeepers.workflow.planreview.PlanningThreadPacketFormatter;
import com.vinekeepers.workflow.planreview.PlanningUserFacingCopy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Composable planning-room cycle: expansion, role passes, synthesis, depth, clarification spread keys.
 * Invoked by {@link com.vinekeepers.workflow.actions.ExecutePlanningRoomCycleAction} and reusable for v2 capabilities.
 */
public final class PlanningCyclePipeline {

    private static final int MAX_BOT_INNER_ROUNDS = 3;
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Partial-cycle YAML: aggregated follow-up strings between expansion / inner-round / finalize actions. */
    public static final String PARTIAL_AGGREGATED_FOLLOWUPS_KEY = "planningPartialAggregatedFollowUpsJson";

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

    private record LoadedCycle(
            String contextId, FeaturePlanState plan, WorkProfileDefinition profile, Map<String, Object> work) {}

    private record InnerRoundResult(
            FeaturePlanState plan,
            boolean depthOk,
            String depthReason,
            String lastRoleRoundSummary,
            String lastSynthLlmLine) {}

    private record ClarificationRoundOutcome(
            FeaturePlanState plan,
            RankedClarification rankedLlm,
            PlanningDeliberationLedgerSync.UpsertResult upsert,
            /** When true, canonical_v1 still has at least one open gap (authoritative for {@code planningUserInputRequired}). */
            boolean canonicalClarificationPending,
            /**
             * Raw ranker signal before canonical repair ({@link #ensureRankedForOpenCanonicalGap}); spread as {@code
             * planningLlmUserInputSuggested}.
             */
            boolean llmUserInputSuggested,
            /** Budget exhausted on a blocking coordinator gap ({@link ClarificationResolutionDecision#BLOCK_AS_UNIMPLEMENTABLE}). */
            boolean hardClarificationBlock) {}

    private final OpenAiChatClient openAiChatClient;
    private final FeaturePlanStateStore planStateStore;
    private final WorkProfileRegistry workProfileRegistry;

    public PlanningCyclePipeline(
            OpenAiChatClient openAiChatClient,
            FeaturePlanStateStore planStateStore,
            WorkProfileRegistry workProfileRegistry) {
        this.openAiChatClient = openAiChatClient;
        this.planStateStore = planStateStore;
        this.workProfileRegistry = workProfileRegistry;
    }

    public Map<String, Object> execute(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = baseSpread();
        LoadedCycle ctx = loadCycleOrAbort(event, state, bind, spread);
        if (ctx == null) {
            finishProgressFingerprint(state, spread);
            return spread;
        }
        String contextId = ctx.contextId();
        WorkProfileDefinition profile = ctx.profile();
        Map<String, Object> work = ctx.work();
        FeaturePlanState plan = ctx.plan();

        int cycleIteration = parseInt(getString(state, "planningRoomCycleIteration"), 0);
        cycleIteration++;
        spread.put("planningRoomCycleIteration", String.valueOf(cycleIteration));

        List<String> aggregatedFollowUps = new ArrayList<>();
        boolean selective = selectiveRerunAfterClarificationEnabled(state, bind);
        boolean justMerged = "true".equalsIgnoreCase(getString(state, "planningJustMergedClarification"));
        boolean skipExpansion = selective && justMerged;
        if (!skipExpansion) {
            runExpansionPhase(event, work, spread, bind, aggregatedFollowUps);
        } else {
            spread.put("planningSelectiveRerunActive", "true");
            spread.put(
                    "planningSelectiveRerunNote",
                    "Thanks — I'm refreshing the draft from your last answer (skipping a full re-scan this pass).");
            mergeExpansionFollowUpsFromWork(work, aggregatedFollowUps);
        }

        String depthReason = "";
        boolean depthOk = false;
        String lastRoleRoundSummary = "";
        String lastSynthLlmLine = "";
        FeaturePlanState planPtr = plan;

        int innerRounds = (selective && justMerged) ? 1 : MAX_BOT_INNER_ROUNDS;
        for (int inner = 0; inner < innerRounds; inner++) {
            InnerRoundResult round =
                    runSingleInnerRound(
                            event, contextId, profile, work, spread, bind, aggregatedFollowUps, planPtr, lastSynthLlmLine);
            planPtr = round.plan();
            depthOk = round.depthOk();
            depthReason = round.depthReason();
            lastRoleRoundSummary = round.lastRoleRoundSummary();
            lastSynthLlmLine = round.lastSynthLlmLine();
            spread.put(
                    "planningCycleProgressSummary",
                    buildInnerRoundProgressSummary(inner + 1, innerRounds, depthOk, lastRoleRoundSummary));
            enrichUserCopyAndProgressLog(work, spread);
            finishProgressFingerprint(work, spread);
            if (depthOk) {
                break;
            }
        }

        spread.put("planningCycleRolePassSummary", lastRoleRoundSummary);
        spread.put("planningCycleSynthesisLlmNote", lastSynthLlmLine);

        plan = planStateStore.getByContextId(contextId).orElse(planPtr);
        ClarificationRoundOutcome clr =
                resolveClarificationRound(contextId, plan, state, profile, aggregatedFollowUps, true);
        plan = clr.plan();
        RankedClarification rankedLlm = clr.rankedLlm();
        PlanningDeliberationLedgerSync.UpsertResult upsert = clr.upsert();
        UnresolvedItemLedger.mergeLedgerIntoSpread(spread, upsert.ledger());
        spread.put("planningClarificationLedgerItemId", upsert.activeItemId().orElse(""));

        CoordinatorClarificationSettings coord = profile.getCoordinatorClarification();
        RankedClarification ranked =
                PlanningGapEvaluator.effectiveRanked(plan, upsert.ledger(), rankedLlm, profile, coord);
        boolean userInputRequired =
                resolvePlanningUserInputRequired(coord.isCanonicalV1(), clr.canonicalClarificationPending(), upsert.ledger());

        PlanGovernanceDeriver.deriveAndPersist(planStateStore, contextId, profile);
        plan = planStateStore.getByContextId(contextId).orElse(plan);

        spread.put("planningCanonicalUserInputRequired", clr.canonicalClarificationPending() ? "true" : "false");
        spread.put("planningLlmUserInputSuggested", clr.llmUserInputSuggested() ? "true" : "false");
        spread.put("planningQuestionsAskedThisRound", rankedLlm.userInputRequired() ? "1" : "0");
        spread.put("planningBlockingQuestionCount", String.valueOf(ranked.blockingQuestionCount()));
        spread.put("planningUserInputRequired", userInputRequired ? "true" : "false");
        spread.put("planningClarificationChoicesJson", ranked.choicesJson());
        spread.put("planningClarificationMetaJson", ranked.metaJson());
        spread.put("planningClarificationUseStructuredChoices", ranked.useStructuredChoices() ? "true" : "false");
        spread.put("planningClarificationQuestionText", ranked.questionText() != null ? ranked.questionText() : "");
        spread.put(
                "planningClarificationOrchestratorPrompt",
                ranked.orchestratorPrompt() != null ? ranked.orchestratorPrompt() : "");
        boolean structuredParseFailed =
                "true".equalsIgnoreCase(getString(spread, PLANNING_STRUCTURED_PASS_PARSE_FAILED_KEY));
        boolean readyToPost = depthOk && !userInputRequired && !structuredParseFailed;
        if (structuredParseFailed) {
            spread.put("planningPacketDepthRetryRecommended", "true");
        }
        applyClarificationStuck(state, spread, ranked, userInputRequired);

        if (!readyToPost && !userInputRequired && cycleIteration >= 4) {
            putPlanningRoomCycleError(spread, "DEPTH_FAIL_AFTER_RETRIES: " + depthReason);
        }

        applyPostDraftGovernor(
                contextId,
                state,
                spread,
                plan,
                clr,
                profile,
                userInputRequired,
                readyToPost,
                depthOk,
                structuredParseFailed,
                depthReason);

        DeliberationEngine.applyDerivedDeliberationSpread(spread);
        spread.put("planningAssumptionsUsed", String.valueOf(rankedLlm.assumptionsToRecord().size()));

        boolean effectiveUser = "true".equalsIgnoreCase(getString(spread, "planningUserInputRequired"));
        boolean effectiveReady = "true".equalsIgnoreCase(getString(spread, "planningReadyToPostPacket"));
        boolean clarificationStuck = "true".equalsIgnoreCase(getString(spread, "planningClarificationStuck"));
        String stuckHint = getString(spread, "planningClarificationStuckHint");
        spread.put(
                "planningOrchestratorRoundSummary",
                buildOrchestratorSummary(
                        plan,
                        depthOk,
                        depthReason,
                        ranked,
                        cycleIteration,
                        effectiveReady,
                        clarificationStuck,
                        stuckHint != null ? stuckHint : "",
                        effectiveUser,
                        structuredParseFailed,
                        getString(state, "planningRepoEvidenceJson")));
        String rolePassErr =
                spread.get("planningRolePassLastError") != null
                        ? spread.get("planningRolePassLastError").toString()
                        : "";
        String llmErr = firstNonBlank(getString(spread, "planningLlmError"), "");
        spread.put(
                "planningCycleUserVisibleFailure",
                buildUserVisibleFailure(
                        spread.get("planningRoomCycleErrorUserMessage") != null
                                ? spread.get("planningRoomCycleErrorUserMessage").toString()
                                : "",
                        llmErr));

        spread.put(
                "planningCycleProgressSummary",
                buildCycleProgressSummary(
                        cycleIteration,
                        depthOk,
                        depthReason,
                        ranked,
                        effectiveUser,
                        spread.get("planningRoomCycleErrorUserMessage") != null
                                ? spread.get("planningRoomCycleErrorUserMessage").toString()
                                : "",
                        rolePassErr,
                        lastRoleRoundSummary,
                        lastSynthLlmLine,
                        llmErr,
                        getString(spread, "planningExpansionFallbackUsed"),
                        getString(spread, "planningLlmSkipReason"),
                        getString(spread, "planningSelectiveRerunNote"),
                        "true".equalsIgnoreCase(getString(spread, PLANNING_PASS_INTERRUPTED_KEY)),
                        structuredParseFailed));
        spread.put(
                "userCopyCoordinatorProgress",
                spread.get("planningCycleProgressSummary") != null
                        ? spread.get("planningCycleProgressSummary").toString()
                        : "");

        enrichUserCopyAndProgressLog(state, spread);
        spread.put("planningJustMergedClarification", "false");
        spread.put("planningSelectiveRerunActive", "false");
        finishProgressFingerprint(state, spread);
        String cycleErr = getString(spread, "planningRoomCycleError");
        if (cycleErr == null || cycleErr.isBlank()) {
            spread.put("planningAutonomousFirstPassCompleted", "true");
            FeaturePlanState persisted = planStateStore.getByContextId(contextId).orElse(null);
            if (persisted != null) {
                planStateStore.update(persisted.withAutonomousPlanningPassCompleted(true));
            }
        }
        return spread;
    }

    /**
     * Partial cycle: request expansion LLM + build request exploration. Resets {@link #PARTIAL_AGGREGATED_FOLLOWUPS_KEY}
     * in the returned spread (merge into session before inner rounds). Bumps {@code planningRoomCycleIteration}.
     */
    public Map<String, Object> runExpansionPhaseOnly(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = baseSpread();
        LoadedCycle ctx = loadCycleOrAbort(event, state, bind, spread);
        if (ctx == null) {
            finishProgressFingerprint(state, spread);
            return spread;
        }
        int cycleIteration = parseInt(getString(state, "planningRoomCycleIteration"), 0) + 1;
        spread.put("planningRoomCycleIteration", String.valueOf(cycleIteration));
        List<String> aggregatedFollowUps = new ArrayList<>();
        runExpansionPhase(event, ctx.work(), spread, bind, aggregatedFollowUps);
        putAggregatedJson(spread, aggregatedFollowUps);
        return spread;
    }

    /**
     * Partial cycle: one Arrietty planning round, expand drafts, synthesis, pre-critique, depth check.
     * Reads/writes {@link #PARTIAL_AGGREGATED_FOLLOWUPS_KEY} via state (after merge) and returned spread.
     */
    public Map<String, Object> runInnerRoundOnce(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = baseSpread();
        LoadedCycle ctx = loadCycleOrAbort(event, state, bind, spread);
        if (ctx == null) {
            finishProgressFingerprint(state, spread);
            return spread;
        }
        String contextId = ctx.contextId();
        List<String> aggregatedFollowUps = readAggregatedFromState(state);
        String prevSynth = getString(state, PARTIAL_LAST_SYNTH_KEY);
        FeaturePlanState plan = ctx.plan();
        InnerRoundResult round =
                runSingleInnerRound(
                        event,
                        contextId,
                        ctx.profile(),
                        ctx.work(),
                        spread,
                        bind,
                        aggregatedFollowUps,
                        plan,
                        prevSynth != null ? prevSynth : "");
        putAggregatedJson(spread, aggregatedFollowUps);
        spread.put(PARTIAL_LAST_ROLE_SUMMARY_KEY, round.lastRoleRoundSummary());
        spread.put(PARTIAL_LAST_SYNTH_KEY, round.lastSynthLlmLine());
        spread.put(PARTIAL_DEPTH_OK_KEY, round.depthOk() ? "true" : "false");
        spread.put(PARTIAL_DEPTH_REASON_KEY, round.depthReason() != null ? round.depthReason() : "");
        return spread;
    }

    /**
     * Partial cycle: rank clarifications, orchestrator summary, progress fingerprint — after expansion + inner round(s).
     */
    public Map<String, Object> finalizePlanningCycleSpread(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = baseSpread();
        LoadedCycle ctx = loadCycleOrAbort(event, state, bind, spread);
        if (ctx == null) {
            finishProgressFingerprint(state, spread);
            return spread;
        }
        String contextId = ctx.contextId();
        int cycleIteration = parseInt(getString(state, "planningRoomCycleIteration"), 0);
        List<String> aggregatedFollowUps = readAggregatedFromState(state);
        boolean depthOk = "true".equalsIgnoreCase(getString(state, PARTIAL_DEPTH_OK_KEY));
        String depthReason = getString(state, PARTIAL_DEPTH_REASON_KEY);
        if (depthReason == null) {
            depthReason = "";
        }
        String lastRoleRoundSummary = getString(state, PARTIAL_LAST_ROLE_SUMMARY_KEY);
        if (lastRoleRoundSummary == null) {
            lastRoleRoundSummary = "";
        }
        String lastSynthLlmLine = getString(state, PARTIAL_LAST_SYNTH_KEY);
        if (lastSynthLlmLine == null) {
            lastSynthLlmLine = "";
        }

        spread.put("planningCycleRolePassSummary", lastRoleRoundSummary);
        spread.put("planningCycleSynthesisLlmNote", lastSynthLlmLine);
        spread.put("planningPacketDepthOk", depthOk ? "true" : "false");
        spread.put("planningPacketDepthReason", depthReason);
        spread.put("planningPacketDepthRetryRecommended", depthOk ? "false" : "true");

        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(ctx.plan());
        WorkProfileDefinition profileFin = ctx.profile();
        ClarificationRoundOutcome clrFin =
                resolveClarificationRound(contextId, plan, state, profileFin, aggregatedFollowUps, false);
        plan = clrFin.plan();
        RankedClarification rankedLlmFin = clrFin.rankedLlm();
        PlanningDeliberationLedgerSync.UpsertResult upsertFin = clrFin.upsert();
        UnresolvedItemLedger.mergeLedgerIntoSpread(spread, upsertFin.ledger());
        spread.put("planningClarificationLedgerItemId", upsertFin.activeItemId().orElse(""));

        CoordinatorClarificationSettings coordFin = profileFin.getCoordinatorClarification();
        RankedClarification ranked =
                PlanningGapEvaluator.effectiveRanked(plan, upsertFin.ledger(), rankedLlmFin, profileFin, coordFin);
        boolean userInputRequired =
                resolvePlanningUserInputRequired(
                        coordFin.isCanonicalV1(), clrFin.canonicalClarificationPending(), upsertFin.ledger());

        spread.put("planningCanonicalUserInputRequired", clrFin.canonicalClarificationPending() ? "true" : "false");
        spread.put("planningLlmUserInputSuggested", clrFin.llmUserInputSuggested() ? "true" : "false");
        spread.put("planningQuestionsAskedThisRound", rankedLlmFin.userInputRequired() ? "1" : "0");
        spread.put("planningBlockingQuestionCount", String.valueOf(ranked.blockingQuestionCount()));
        spread.put("planningUserInputRequired", userInputRequired ? "true" : "false");
        spread.put("planningClarificationChoicesJson", ranked.choicesJson());
        spread.put("planningClarificationMetaJson", ranked.metaJson());
        spread.put("planningClarificationUseStructuredChoices", ranked.useStructuredChoices() ? "true" : "false");
        spread.put("planningClarificationQuestionText", ranked.questionText() != null ? ranked.questionText() : "");
        spread.put(
                "planningClarificationOrchestratorPrompt",
                ranked.orchestratorPrompt() != null ? ranked.orchestratorPrompt() : "");
        boolean structuredParseFailedFinalize =
                "true".equalsIgnoreCase(getString(state, PLANNING_STRUCTURED_PASS_PARSE_FAILED_KEY))
                        || "true".equalsIgnoreCase(getString(spread, PLANNING_STRUCTURED_PASS_PARSE_FAILED_KEY));
        spread.put(
                PLANNING_STRUCTURED_PASS_PARSE_FAILED_KEY, structuredParseFailedFinalize ? "true" : "false");
        boolean readyToPost = depthOk && !userInputRequired && !structuredParseFailedFinalize;
        if (structuredParseFailedFinalize) {
            spread.put("planningPacketDepthRetryRecommended", "true");
        }
        applyClarificationStuck(state, spread, ranked, userInputRequired);

        if (!readyToPost && !userInputRequired && cycleIteration >= 4) {
            putPlanningRoomCycleError(spread, "DEPTH_FAIL_AFTER_RETRIES: " + depthReason);
        }

        applyPostDraftGovernor(
                contextId,
                state,
                spread,
                plan,
                clrFin,
                profileFin,
                userInputRequired,
                readyToPost,
                depthOk,
                structuredParseFailedFinalize,
                depthReason);

        DeliberationEngine.applyDerivedDeliberationSpread(spread);
        spread.put("planningAssumptionsUsed", String.valueOf(rankedLlmFin.assumptionsToRecord().size()));

        boolean effectiveUserFin = "true".equalsIgnoreCase(getString(spread, "planningUserInputRequired"));
        boolean effectiveReadyFin = "true".equalsIgnoreCase(getString(spread, "planningReadyToPostPacket"));
        boolean clarificationStuck = "true".equalsIgnoreCase(getString(spread, "planningClarificationStuck"));
        String stuckHint = getString(spread, "planningClarificationStuckHint");
        spread.put(
                "planningOrchestratorRoundSummary",
                buildOrchestratorSummary(
                        plan,
                        depthOk,
                        depthReason,
                        ranked,
                        cycleIteration,
                        effectiveReadyFin,
                        clarificationStuck,
                        stuckHint != null ? stuckHint : "",
                        effectiveUserFin,
                        structuredParseFailedFinalize,
                        getString(state, "planningRepoEvidenceJson")));
        String rolePassErr =
                spread.get("planningRolePassLastError") != null
                        ? spread.get("planningRolePassLastError").toString()
                        : "";
        String llmErr = firstNonBlank(getString(spread, "planningLlmError"), "");
        spread.put(
                "planningCycleUserVisibleFailure",
                buildUserVisibleFailure(
                        spread.get("planningRoomCycleErrorUserMessage") != null
                                ? spread.get("planningRoomCycleErrorUserMessage").toString()
                                : "",
                        llmErr));

        spread.put(
                "planningCycleProgressSummary",
                buildCycleProgressSummary(
                        cycleIteration,
                        depthOk,
                        depthReason,
                        ranked,
                        effectiveUserFin,
                        spread.get("planningRoomCycleErrorUserMessage") != null
                                ? spread.get("planningRoomCycleErrorUserMessage").toString()
                                : "",
                        rolePassErr,
                        lastRoleRoundSummary,
                        lastSynthLlmLine,
                        llmErr,
                        getString(spread, "planningExpansionFallbackUsed"),
                        getString(spread, "planningLlmSkipReason"),
                        "",
                        "true".equalsIgnoreCase(getString(spread, PLANNING_PASS_INTERRUPTED_KEY)),
                        structuredParseFailedFinalize));
        spread.put(
                "userCopyCoordinatorProgress",
                spread.get("planningCycleProgressSummary") != null
                        ? spread.get("planningCycleProgressSummary").toString()
                        : "");

        enrichUserCopyAndProgressLog(state, spread);
        finishProgressFingerprint(state, spread);
        String cycleErrFin = getString(spread, "planningRoomCycleError");
        if (cycleErrFin == null || cycleErrFin.isBlank()) {
            spread.put("planningAutonomousFirstPassCompleted", "true");
            FeaturePlanState persistedFin = planStateStore.getByContextId(contextId).orElse(null);
            if (persistedFin != null) {
                planStateStore.update(persistedFin.withAutonomousPlanningPassCompleted(true));
            }
        }
        return spread;
    }

    private ClarificationRoundOutcome resolveClarificationRound(
            String contextId,
            FeaturePlanState plan,
            Map<String, Object> state,
            WorkProfileDefinition profile,
            List<String> aggregatedFollowUps,
            boolean draftingCompletedThisInvocation) {
        UnresolvedItemLedger ledger = UnresolvedItemLedger.readFrom(state);
        CoordinatorClarificationSettings coord = profile.getCoordinatorClarification();
        RankedClarification rankedLlm;
        if (coord.isCanonicalV1()) {
            boolean semanticAllowed = semanticClarificationAllowed(plan, state, draftingCompletedThisInvocation);
            boolean critiqueSweep =
                    "true".equalsIgnoreCase(getString(state, "planningCritiqueOpenClarificationSweep"));
            for (int sweep = 0; sweep < 3; sweep++) {
                List<AssessedGap> assessedSweep =
                        ClarificationEngineAssessor.assessCanonicalGaps(
                                plan,
                                coord,
                                aggregatedFollowUps,
                                semanticAllowed,
                                getString(state, "planningRepoEvidenceJson"),
                                critiqueSweep);
                boolean progressed = false;
                for (AssessedGap ag : assessedSweep) {
                    if (ag.decision() == ClarificationResolutionDecision.ASSUME_AND_CONTINUE
                            && ag.assumptionToRecord() != null
                            && !ag.assumptionToRecord().isBlank()) {
                        plan = appendAssumption(plan, ag.assumptionToRecord());
                        progressed = true;
                    } else if (ag.decision() == ClarificationResolutionDecision.LOW_PRIORITY_DEFER) {
                        plan =
                                plan.withClarificationEngineNote(
                                        "Deferred coordinator gap `" + ag.gap().gapId() + "` (low priority / budget policy).");
                    } else if (ag.decision() == ClarificationResolutionDecision.BLOCK_AS_UNIMPLEMENTABLE) {
                        plan =
                                plan.withClarificationEngineNote(
                                        "Clarification budget exhausted on blocking gap `" + ag.gap().gapId() + "`.");
                    }
                }
                if (progressed) {
                    planStateStore.update(plan);
                    plan = planStateStore.getByContextId(contextId).orElse(plan);
                } else {
                    break;
                }
            }
            List<AssessedGap> assessedFinal =
                    ClarificationEngineAssessor.assessCanonicalGaps(
                            plan,
                            coord,
                            aggregatedFollowUps,
                            semanticAllowed,
                            getString(state, "planningRepoEvidenceJson"),
                            critiqueSweep);
            for (AssessedGap ag : assessedFinal) {
                if (ag.decision() == ClarificationResolutionDecision.ASSUME_AND_CONTINUE
                        && ag.assumptionToRecord() != null
                        && !ag.assumptionToRecord().isBlank()) {
                    plan = appendAssumption(plan, ag.assumptionToRecord());
                } else if (ag.decision() == ClarificationResolutionDecision.LOW_PRIORITY_DEFER) {
                    plan =
                            plan.withClarificationEngineNote(
                                    "Deferred coordinator gap `" + ag.gap().gapId() + "` (low priority / budget policy).");
                } else if (ag.decision() == ClarificationResolutionDecision.BLOCK_AS_UNIMPLEMENTABLE) {
                    plan =
                            plan.withClarificationEngineNote(
                                    "Clarification budget exhausted on blocking gap `" + ag.gap().gapId() + "`.");
                }
            }
            planStateStore.update(plan);
            plan = planStateStore.getByContextId(contextId).orElse(plan);
            assessedFinal =
                    ClarificationEngineAssessor.assessCanonicalGaps(
                            plan,
                            coord,
                            aggregatedFollowUps,
                            semanticAllowed,
                            getString(state, "planningRepoEvidenceJson"),
                            critiqueSweep);
            boolean hardClarificationBlock =
                    assessedFinal.stream()
                            .anyMatch(
                                    ag ->
                                            ag.decision()
                                                    == ClarificationResolutionDecision.BLOCK_AS_UNIMPLEMENTABLE);
            List<CoordinatorClarificationGapEvaluator.OpenGap> openRawFinal =
                    CoordinatorClarificationGapEvaluator.evaluateOpenGaps(
                            plan, coord, aggregatedFollowUps, semanticAllowed);
            CoordinatorClarificationGapEvaluator.OpenGap topAskFinal = null;
            for (AssessedGap ag : assessedFinal) {
                if (ag.decision() == ClarificationResolutionDecision.ASK_USER) {
                    topAskFinal = ag.gap();
                    break;
                }
            }
            ledger =
                    PlanningDeliberationLedgerSync.reconcileCanonicalOpenGaps(
                            ledger,
                            topAskFinal != null
                                    ? java.util.Set.of(topAskFinal.gapId())
                                    : CoordinatorClarificationGapEvaluator.openGapIds(openRawFinal));
            RankedClarification rankedBeforeEnsure =
                    topAskFinal == null
                            ? emptyRankedClarification()
                            : rankCanonicalOpenTopGap(plan, ledger, profile, coord, topAskFinal);
            boolean llmUserInputSuggested = rankedBeforeEnsure.userInputRequired();
            rankedLlm = rankedBeforeEnsure;
            if (topAskFinal != null) {
                rankedLlm =
                        ensureRankedForOpenCanonicalGap(
                                plan, ledger, profile, coord, topAskFinal, rankedBeforeEnsure);
            }
            PlanningDeliberationLedgerSync.UpsertResult upsert;
            if (topAskFinal == null) {
                upsert = PlanningDeliberationLedgerSync.upsertOpenQuestion(ledger, rankedLlm);
            } else {
                upsert =
                        PlanningDeliberationLedgerSync.upsertOpenQuestionForCanonicalGap(
                                ledger, rankedLlm, topAskFinal.gapId(), topAskFinal.blocking());
            }
            if (topAskFinal != null && rankedLlm.userInputRequired()) {
                plan =
                        plan.withClarificationQuestionSurfaced(
                                        "ask:" + topAskFinal.gapId() + ":" + java.time.Instant.now())
                                .withPlanningIntakeStage(PlanningIntakeStage.CLARIFYING, java.time.Instant.now());
                planStateStore.update(plan);
                plan = planStateStore.getByContextId(contextId).orElse(plan);
            }
            boolean canonicalPending = topAskFinal != null;
            return new ClarificationRoundOutcome(
                    plan, rankedLlm, upsert, canonicalPending, llmUserInputSuggested, hardClarificationBlock);
        }
        rankedLlm =
                PlanningQuestionRankingPolicy.rank(
                        plan,
                        aggregatedFollowUps,
                        1,
                        ledger,
                        profile.isBoundedClarificationChoicesEnabled(),
                        profile.isInferBoundedChoiceFromOrInTextEnabled());
        for (String assumption : rankedLlm.assumptionsToRecord()) {
            plan = appendAssumption(plan, assumption);
        }
        planStateStore.update(plan);
        plan = planStateStore.getByContextId(contextId).orElse(plan);
        PlanningDeliberationLedgerSync.UpsertResult upsert =
                PlanningDeliberationLedgerSync.upsertOpenQuestion(ledger, rankedLlm);
        plan = ClarificationCoordinatorLedger.persist(planStateStore, contextId, plan, List.of(), null);
        return new ClarificationRoundOutcome(plan, rankedLlm, upsert, false, rankedLlm.userInputRequired(), false);
    }

    private void applyPostDraftGovernor(
            String contextId,
            Map<String, Object> persistedSessionState,
            Map<String, Object> spread,
            FeaturePlanState plan,
            ClarificationRoundOutcome clr,
            WorkProfileDefinition profile,
            boolean userInputRequired,
            boolean readyToPost,
            boolean depthOk,
            boolean structuredParseFailed,
            String depthReason) {
        Map<String, Object> signal = new LinkedHashMap<>();
        if (persistedSessionState != null) {
            signal.putAll(persistedSessionState);
        }
        if (spread != null) {
            signal.putAll(spread);
        }
        String cycleErr = getString(spread, "planningRoomCycleError");
        if (cycleErr == null) {
            cycleErr = "";
        }
        PlanningPostDraftGovernor.Result gov =
                PlanningPostDraftGovernor.derive(
                        persistedSessionState != null ? persistedSessionState : Map.of(),
                        signal,
                        plan,
                        clr.upsert().ledger(),
                        userInputRequired,
                        readyToPost,
                        depthOk,
                        structuredParseFailed,
                        depthReason != null ? depthReason : "",
                        cycleErr,
                        firstNonBlank(getString(spread, "planningSynthesisFailureCategory"), ""),
                        "true".equalsIgnoreCase(getString(spread, "planningRecoverableDraftAfterSynthesis")),
                        "true".equalsIgnoreCase(getString(spread, "planningSuppressAutonomousRedraftNotice")),
                        clr.hardClarificationBlock());
        boolean effectiveUser = userInputRequired || gov.forceUserInputRequired();
        if (gov.forceUserInputRequired()) {
            spread.put("planningUserInputRequired", "true");
            if (profile.getCoordinatorClarification().isCanonicalV1()) {
                spread.put("planningCanonicalUserInputRequired", "true");
            }
            String qt = getString(spread, "planningClarificationQuestionText");
            if (qt == null || qt.isBlank()) {
                String clar =
                        PlanningPostDraftGovernor.firstUserFacingClarificationTextOrEmpty(
                                clr.upsert().ledger(), plan);
                if (!clar.isBlank()) {
                    spread.put("planningClarificationQuestionText", clar);
                }
            }
        } else {
            spread.put("planningUserInputRequired", userInputRequired ? "true" : "false");
        }
        if (gov.action() == PlanningPostDraftAction.ASK_ONE_QUESTION
                && contextId != null
                && !contextId.isBlank()
                && plan != null
                && plan.getPlanningIntakeStage() != PlanningIntakeStage.CLARIFYING) {
            FeaturePlanState clarifying = plan.withPlanningIntakeStage(PlanningIntakeStage.CLARIFYING, java.time.Instant.now());
            planStateStore.update(clarifying);
            plan = planStateStore.getByContextId(contextId).orElse(clarifying);
        }
        boolean effectiveReady = depthOk && !effectiveUser && !structuredParseFailed;
        if (gov.action() == PlanningPostDraftAction.BLOCK) {
            spread.put("planningUserInputRequired", "false");
            spread.put("planningCanonicalUserInputRequired", "false");
            effectiveReady = false;
            String synthCat = blankToEmpty(getString(spread, "planningSynthesisFailureCategory"));
            if (!synthCat.isBlank()
                    && contextId != null
                    && !contextId.isBlank()
                    && plan != null) {
                boolean rd = "true".equalsIgnoreCase(getString(spread, "planningRecoverableDraftAfterSynthesis"));
                FeaturePlanState updated =
                        plan.withPlannerRecoveryFields(
                                PlanningFailureCategory.parse(synthCat),
                                getString(spread, "planningPhase"),
                                rd,
                                PlanningUserFacingCopy.humanizePlanningRoomCycleErrorCode(synthCat));
                planStateStore.update(updated);
                plan = planStateStore.getByContextId(contextId).orElse(updated);
            }
        }
        spread.put("planningReadyToPostPacket", effectiveReady ? "true" : "false");
        spread.put(
                "planningPhase",
                effectiveUser
                        ? "WAITING_FOR_CLARIFICATION"
                        : (effectiveReady ? "READY_FOR_REVIEW" : "REVISING"));
        spread.put(
                "planningRevisionNeeded",
                (!depthOk || effectiveUser || structuredParseFailed) ? "true" : "false");
        PlanningReadinessSpread.applyCycleReadiness(spread, gov.action(), effectiveReady, effectiveUser);
        spread.put(PlanningPostDraftGovernor.SPREAD_KEY, gov.action().name());
        spread.put(
                PlanningPostDraftGovernor.NOTICE_MARKDOWN_KEY,
                gov.noticeMarkdown() != null ? gov.noticeMarkdown() : "");
        boolean wantsRevision = !effectiveReady && !effectiveUser;
        String situation =
                PlanningPostDraftGovernor.revisionSituationFingerprint(
                        depthOk,
                        structuredParseFailed,
                        depthReason != null ? depthReason : "",
                        effectiveUser,
                        effectiveReady,
                        clr.upsert().ledger());
        PlanningPostDraftGovernor.writePersistenceKeys(
                spread, signal, plan, effectiveReady, wantsRevision, situation);
    }

    static boolean resolvePlanningUserInputRequired(
            boolean canonicalV1, boolean canonicalGapPending, UnresolvedItemLedger ledger) {
        if (canonicalV1) {
            return canonicalGapPending;
        }
        return PlanningGapEvaluator.requiresUserInputForPlanningClarification(ledger);
    }

    static boolean semanticClarificationAllowed(
            FeaturePlanState plan, Map<String, Object> state, boolean draftingCompletedThisInvocation) {
        if (draftingCompletedThisInvocation) {
            return true;
        }
        if (plan != null && plan.isAutonomousPlanningPassCompleted()) {
            return true;
        }
        return "true".equalsIgnoreCase(getString(state, "planningAutonomousFirstPassCompleted"));
    }

    static String effectiveCanonicalQuestionText(
            String templateQuestion,
            String gapId,
            CoordinatorClarificationGapRule rule,
            UnresolvedItemLedger ledger) {
        String template = templateQuestion != null ? templateQuestion.trim() : "";
        if (template.isBlank() || gapId == null || gapId.isBlank()) {
            return template;
        }
        Optional<String> last =
                PlanningDeliberationLedgerSync.lastMergedQuestionTextForPlanningGap(ledger, gapId);
        if (last.isEmpty()) {
            return template;
        }
        if (!normalizeClarificationQuestion(template).equals(normalizeClarificationQuestion(last.get()))) {
            return template;
        }
        if (rule != null && rule.getNarrowEscalationTemplate() != null && !rule.getNarrowEscalationTemplate().isBlank()) {
            return rule.getNarrowEscalationTemplate();
        }
        return "You already answered part of this. To finish narrowing scope, can you spell out one concrete constraint "
                + "(where it should live, who changes it, or an example edge case)?";
    }

    private static final String CANONICAL_CLARIFICATION_FALLBACK_QUESTION =
            "I need one clarification before I continue: what exact behavior should be configurable versus fixed at runtime"
                    + " for this feature?";

    /**
     * When canonical gaps are open but {@link PlanningQuestionRankingPolicy#rank} dropped the template (score threshold,
     * defaults, fingerprints), rebuild a ranker-backed or synthetic clarification so upsert and user-facing copy stay
     * aligned with {@code planningUserInputRequired}.
     */
    private static RankedClarification ensureRankedForOpenCanonicalGap(
            FeaturePlanState plan,
            UnresolvedItemLedger ledger,
            WorkProfileDefinition profile,
            CoordinatorClarificationSettings coord,
            CoordinatorClarificationGapEvaluator.OpenGap top,
            RankedClarification ranked) {
        String qt = ranked.questionText() != null ? ranked.questionText().trim() : "";
        if (ranked.userInputRequired() && !qt.isBlank()) {
            return ranked;
        }
        CoordinatorClarificationGapRule rule = coord.findGapRule(top.gapId()).orElse(null);
        String synth = effectiveCanonicalQuestionText(top.questionText(), top.gapId(), rule, ledger);
        if (synth.isBlank()) {
            synth = top.questionText() != null ? top.questionText().trim() : "";
        }
        if (synth.isBlank()) {
            synth =
                    PlanningDeliberationLedgerSync.lastMergedQuestionTextForPlanningGap(ledger, top.gapId())
                            .orElse("")
                            .trim();
        }
        if (synth.isBlank()) {
            synth = CANONICAL_CLARIFICATION_FALLBACK_QUESTION;
        }
        boolean bounded =
                rule != null
                        && rule.isUseBoundedChoiceUi()
                        && profile != null
                        && profile.isBoundedClarificationChoicesEnabled();
        boolean inferOr = bounded && rule != null && rule.isInferOrChoices();
        RankedClarification reranked =
                PlanningQuestionRankingPolicy.rank(plan, List.of(synth), 1, ledger, bounded, inferOr);
        reranked = mergeRankedAssumptions(ranked, reranked);
        String rqt = reranked.questionText() != null ? reranked.questionText().trim() : "";
        if (reranked.userInputRequired() && !rqt.isBlank()) {
            return withCoordinatorGapMeta(reranked, top.gapId(), top.blocking());
        }
        return withCoordinatorGapMeta(
                syntheticPlainTextClarificationForGap(top, synth, ranked.assumptionsToRecord()), top.gapId(), top.blocking());
    }

    private static RankedClarification mergeRankedAssumptions(RankedClarification prior, RankedClarification next) {
        List<String> merged = new ArrayList<>(prior.assumptionsToRecord());
        merged.addAll(next.assumptionsToRecord());
        return new RankedClarification(
                next.userInputRequired(),
                next.orchestratorPrompt(),
                next.choicesJson(),
                next.metaJson(),
                next.blockingQuestionCount(),
                merged,
                next.useStructuredChoices(),
                next.questionText());
    }

    private static RankedClarification syntheticPlainTextClarificationForGap(
            CoordinatorClarificationGapEvaluator.OpenGap top, String questionText, List<String> assumptions) {
        String q = questionText != null ? questionText.trim() : "";
        if (q.isBlank()) {
            q = CANONICAL_CLARIFICATION_FALLBACK_QUESTION;
        }
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("questionText", q);
            meta.put("defaultAssumption", "");
            meta.put("optA", "");
            meta.put("optB", "");
            meta.put("optC", "");
            String metaJson = JSON.writeValueAsString(meta);
            int blocking = top.blocking() ? 1 : 0;
            return new RankedClarification(
                    true, "", "[]", metaJson, blocking, new ArrayList<>(assumptions), false, q);
        } catch (JsonProcessingException e) {
            int blocking = top.blocking() ? 1 : 0;
            return new RankedClarification(true, "", "[]", "{}", blocking, new ArrayList<>(assumptions), false, q);
        }
    }

    private static RankedClarification rankCanonicalOpenTopGap(
            FeaturePlanState plan,
            UnresolvedItemLedger ledger,
            WorkProfileDefinition profile,
            CoordinatorClarificationSettings coord,
            CoordinatorClarificationGapEvaluator.OpenGap top) {
        CoordinatorClarificationGapRule rule = coord.findGapRule(top.gapId()).orElse(null);
        String q = effectiveCanonicalQuestionText(top.questionText(), top.gapId(), rule, ledger);
        boolean bounded =
                rule != null
                        && rule.isUseBoundedChoiceUi()
                        && profile != null
                        && profile.isBoundedClarificationChoicesEnabled();
        boolean inferOr = bounded && rule != null && rule.isInferOrChoices();
        RankedClarification ranked =
                PlanningQuestionRankingPolicy.rank(plan, List.of(q), 1, ledger, bounded, inferOr);
        return withCoordinatorGapMeta(ranked, top.gapId(), top.blocking());
    }

    private static RankedClarification emptyRankedClarification() {
        return new RankedClarification(false, "", "[]", "{}", 0, List.of(), false, "");
    }

    private static RankedClarification withCoordinatorGapMeta(
            RankedClarification ranked, String gapId, boolean gapRuleBlocking) {
        if (ranked == null) {
            return emptyRankedClarification();
        }
        int blocking =
                gapRuleBlocking
                        ? Math.max(1, ranked.blockingQuestionCount())
                        : ranked.blockingQuestionCount();
        if (gapId == null || gapId.isBlank()) {
            if (blocking != ranked.blockingQuestionCount()) {
                return new RankedClarification(
                        ranked.userInputRequired(),
                        ranked.orchestratorPrompt(),
                        ranked.choicesJson(),
                        ranked.metaJson(),
                        blocking,
                        ranked.assumptionsToRecord(),
                        ranked.useStructuredChoices(),
                        ranked.questionText());
            }
            return ranked;
        }
        try {
            Map<String, Object> meta =
                    ranked.metaJson() == null || ranked.metaJson().isBlank()
                            ? new LinkedHashMap<>()
                            : JSON.readValue(ranked.metaJson(), new TypeReference<>() {});
            meta.put("gapId", gapId.trim());
            String metaJson = JSON.writeValueAsString(meta);
            return new RankedClarification(
                    ranked.userInputRequired(),
                    ranked.orchestratorPrompt(),
                    ranked.choicesJson(),
                    metaJson,
                    blocking,
                    ranked.assumptionsToRecord(),
                    ranked.useStructuredChoices(),
                    ranked.questionText());
        } catch (JsonProcessingException e) {
            return ranked;
        }
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

    private void runExpansionPhase(
            Event event,
            Map<String, Object> work,
            Map<String, Object> spread,
            Map<String, Object> bind,
            List<String> aggregatedFollowUps) {
        spread.put("planningPhase", "REQUEST_EXPANSION");
        Object expansionObj =
                new RunRequestExpansionLlmAction(openAiChatClient, planStateStore, workProfileRegistry).run(event, work, bind);
        if (expansionObj instanceof Map<?, ?> expMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> exp = (Map<String, Object>) expMap;
            mergeSpreadIntoWorkAndOuter(exp, work, spread);
        }
        mergeExpansionFollowUpsFromWork(work, aggregatedFollowUps);
        new BuildRequestExplorationAction(planStateStore, workProfileRegistry).run(event, work, bind);
    }

    private InnerRoundResult runSingleInnerRound(
            Event event,
            String contextId,
            WorkProfileDefinition profile,
            Map<String, Object> work,
            Map<String, Object> spread,
            Map<String, Object> bind,
            List<String> aggregatedFollowUps,
            FeaturePlanState plan,
            String previousSynthLine) {
        spread.put("planningPhase", "DRAFTING");
        FeaturePlanState planPtr = planStateStore.getByContextId(contextId).orElse(plan);
        List<String> roleTags = new ArrayList<>();
        List<PlanningCoordinatorRole> passOrder = ConfigurablePassRunner.resolveOrder(work, bind);
        spread.put("planningRolePassOrderResolved", passOrder.toString());
        for (PlanningCoordinatorRole passRole : passOrder) {
            runRole(passRole, planPtr, profile, event, work, spread, aggregatedFollowUps, roleTags);
            planPtr = planStateStore.getByContextId(contextId).orElse(planPtr);
        }
        String lastRoleRoundSummary = summarizeRoleRound(passOrder, roleTags);

        new ExpandPlanningDraftsAction(planStateStore, workProfileRegistry).run(event, work, bind);

        Object synthObj =
                new RunLlmPlanningSynthesisAction(openAiChatClient, planStateStore, workProfileRegistry)
                        .run(event, work, bind);
        @SuppressWarnings("unchecked")
        Map<String, Object> synthSpread = synthObj instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        mergeSpreadIntoWorkAndOuter(synthSpread, work, spread);
        mergeSynthFollowUps(spread, aggregatedFollowUps);
        applyImmediateSynthesisFailure(contextId, spread, synthSpread);
        String lastSynthLlmLine = pickSynthLlmLine(synthSpread, previousSynthLine);

        new SynthesizePreCritiqueArtifactsAction(planStateStore, workProfileRegistry).run(event, work, bind);

        planPtr = planStateStore.getByContextId(contextId).orElse(planPtr);
        boolean relaxOpenQ = PlanningPacketDepthEvaluator.relaxOpenQuestionSupplementalChecks(work);
        PlanningPacketDepthEvaluator.DepthResult dr =
                PlanningPacketDepthEvaluator.evaluate(planPtr, profile, relaxOpenQ);
        boolean depthOk = dr.ok();
        String depthReason = dr.reason() != null ? dr.reason() : "";
        spread.put("planningPacketDepthOk", depthOk ? "true" : "false");
        spread.put("planningPacketDepthReason", depthReason);
        spread.put("planningPacketDepthRetryRecommended", depthOk ? "false" : "true");
        return new InnerRoundResult(planPtr, depthOk, depthReason, lastRoleRoundSummary, lastSynthLlmLine);
    }

    private static List<String> readAggregatedFromState(Map<String, Object> state) {
        if (state == null) {
            return new ArrayList<>();
        }
        Object raw = state.get(PARTIAL_AGGREGATED_FOLLOWUPS_KEY);
        if (raw == null || raw.toString().isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<String> list = JSON.readValue(raw.toString(), new TypeReference<>() {});
            return list != null ? new ArrayList<>(list) : new ArrayList<>();
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    private static void putAggregatedJson(Map<String, Object> spread, List<String> aggregatedFollowUps) {
        try {
            spread.put(
                    PARTIAL_AGGREGATED_FOLLOWUPS_KEY,
                    JSON.writeValueAsString(aggregatedFollowUps != null ? aggregatedFollowUps : List.of()));
        } catch (JsonProcessingException e) {
            spread.put(PARTIAL_AGGREGATED_FOLLOWUPS_KEY, "[]");
        }
    }

    private static void mergeSpreadIntoWorkAndOuter(
            Map<String, Object> from, Map<String, Object> work, Map<String, Object> spread) {
        if (from == null) {
            return;
        }
        for (Map.Entry<String, Object> e : from.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String k = e.getKey().toString();
            work.put(k, e.getValue());
            spread.put(k, e.getValue());
        }
    }

    private static void enrichUserCopyAndProgressLog(Map<String, Object> state, Map<String, Object> spread) {
        UnresolvedItemLedger ledger = UnresolvedItemLedger.readFrom(spread);
        StringBuilder blockers = new StringBuilder();
        for (UnresolvedItem it : ledger.items()) {
            UnresolvedItemStatus s = it.getStatus();
            if (s == UnresolvedItemStatus.OPEN || s == UnresolvedItemStatus.BLOCKED) {
                String q = it.getQuestionText();
                if (q != null && !q.isBlank()) {
                    if (blockers.length() > 0) {
                        blockers.append("; ");
                    }
                    blockers.append(q.trim());
                }
            }
        }
        spread.put("userCopyBlockerSummary", blockers.toString());
        String lid = getString(spread, "planningClarificationLedgerItemId");
        spread.put("userCopyClarificationLedgerId", lid != null ? lid : "");

        String delta =
                firstNonBlank(
                        getString(spread, "planningSelectiveRerunNote"),
                        truncateOneLine(getString(spread, "planningCycleProgressSummary"), 220));
        spread.put("userCopyProgressDelta", delta != null ? delta : "");

        ProgressEventLog log = ProgressEventLog.readFrom(state);
        String line =
                firstNonBlank(
                        getString(spread, "planningSelectiveRerunNote"),
                        truncateOneLine(getString(spread, "planningCycleProgressSummary"), 300));
        if (line != null && !line.isBlank()) {
            Map<String, String> refs = new LinkedHashMap<>();
            String dphase = getString(spread, "deliberationPhase");
            if (dphase != null && !dphase.isBlank()) {
                refs.put("phase", dphase);
            }
            log = log.withAppendedTyped("pass_status", line, "info", refs);
        }
        ProgressEventLog.mergeIntoSpread(spread, log);
        spread.put("userCopyProgressLine", log.latestMessage());
    }

    private static void finishProgressFingerprint(Map<String, Object> state, Map<String, Object> spread) {
        String summary =
                spread.get("planningCycleProgressSummary") != null
                        ? spread.get("planningCycleProgressSummary").toString()
                        : "";
        String progressBody =
                firstNonBlank(getString(spread, "userCopyCoordinatorProgress"), summary);
        if (progressBody == null) {
            progressBody = "";
        }
        String fullForHash = ProgressDedupeHelper.normalizeProgressBody("**Update:** " + progressBody);
        String fp = ProgressDedupeHelper.fingerprintForBody(fullForHash);
        spread.put("planningProgressPostFingerprint", fp);
        String last = state != null ? getString(state, "planningLastProgressPostHash") : null;
        boolean worthy = ProgressDedupeHelper.isPostWorthy(last, fp);
        spread.put("planningProgressPostWorthy", worthy ? "true" : "false");
    }

    private static String buildUserVisibleFailure(String cycleError, String planningLlmError) {
        StringBuilder sb = new StringBuilder();
        if (cycleError != null && !cycleError.isBlank()) {
            sb.append(
                    truncateOneLine(
                            PlanningUserFacingCopy.humanizePlanningRoomCycleErrorLine(cycleError), 200));
        }
        if (planningLlmError != null && !planningLlmError.isBlank()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(
                    truncateOneLine(
                            PlanningUserFacingCopy.humanizePlanningCycleFailureFragment(planningLlmError), 200));
        }
        return sb.toString().trim();
    }

    private static void applyClarificationStuck(
            Map<String, Object> state,
            Map<String, Object> spread,
            RankedClarification ranked,
            boolean userInputRequired) {
        String prevQ = normalizeClarificationQuestion(getString(state, "planningPreviousClarificationQuestionText"));
        String currQ = normalizeClarificationQuestion(ranked.questionText());
        boolean sameAsPrev = userInputRequired && !prevQ.isEmpty() && currQ.equals(prevQ);
        int prevCount = parseInt(getString(state, "planningClarificationRepeatCount"), 0);
        int newCount = sameAsPrev ? prevCount + 1 : 0;
        spread.put("planningClarificationRepeatCount", String.valueOf(newCount));
        boolean stuck = sameAsPrev && newCount >= 1;
        spread.put("planningClarificationStuck", stuck ? "true" : "false");
        spread.put(
                "planningClarificationStuckHint",
                stuck
                        ? "Same question as before — reply with a concrete example, who decides, or one edge case you care about."
                        : "");
    }

    private static String pickSynthLlmLine(Map<String, Object> synthSpread, String previous) {
        if (synthSpread == null) {
            return previous;
        }
        String err = getString(synthSpread, "planningLlmError");
        if (err != null && !err.isBlank()) {
            return truncateOneLine(PlanningUserFacingCopy.humanizePlanningCycleFailureFragment(err), 140);
        }
        String skip = getString(synthSpread, "planningLlmSkipReason");
        if (skip != null && !skip.isBlank() && !"OK".equalsIgnoreCase(skip)) {
            return truncateOneLine(
                    PlanningUserFacingCopy.humanizePlanningCycleFailureFragment("Synthesis skipped: " + skip), 140);
        }
        return previous;
    }

    private void applyImmediateSynthesisFailure(
            String contextId, Map<String, Object> spread, Map<String, Object> synthSpread) {
        if (synthSpread == null || spread == null) {
            return;
        }
        String llmErr = getString(synthSpread, "planningLlmError");
        if (llmErr == null || llmErr.isBlank()) {
            return;
        }
        int applied = parseInt(getString(synthSpread, "planningLlmUpsertCount"), 0);
        if (applied > 0) {
            return;
        }
        String cat = blankToEmpty(getString(spread, "planningSynthesisFailureCategory"));
        boolean structuredRoleFailed =
                "true".equalsIgnoreCase(getString(spread, PLANNING_STRUCTURED_PASS_PARSE_FAILED_KEY));
        FeaturePlanState planForRecover = planStateStore.getByContextId(contextId).orElse(null);
        boolean recoverable = hasRecoverablePlanningDraft(planForRecover) && !structuredRoleFailed;
        spread.put("planningRecoverableDraftAfterSynthesis", recoverable ? "true" : "false");
        if ("SYNTHESIS_JSON_INVALID".equals(cat) || "SYNTHESIS_REPAIR_EXHAUSTED".equals(cat)) {
            spread.put("planningSuppressAutonomousRedraftNotice", "true");
        }
        if (!cat.isBlank()) {
            spread.put("planningSynthesisFailureCategory", cat);
            return;
        }
        String existing = getString(spread, "planningRoomCycleError");
        if (existing != null && !existing.isBlank()) {
            return;
        }
        putPlanningRoomCycleError(spread, "SYNTHESIS_UPSERTS_NOT_APPLIED");
    }

    private static boolean hasRecoverablePlanningDraft(FeaturePlanState plan) {
        if (plan == null) {
            return false;
        }
        if (PlanningPostDraftGovernor.hasStructuredMaterialPlanningGaps(plan)) {
            return true;
        }
        String ex = PlanningArtifactTexts.artifactField(plan, "request_exploration", "analysis", "exploration_body");
        String fs = PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "feature_summary");
        return (ex != null && !ex.isBlank()) || (fs != null && !fs.isBlank());
    }

    private static void mergeExpansionFollowUpsFromWork(Map<String, Object> work, List<String> aggregated) {
        if (work == null || aggregated == null) {
            return;
        }
        Object raw = work.get("planningExpansionFollowUpsJson");
        if (raw == null) {
            return;
        }
        try {
            List<String> qs = JSON.readValue(raw.toString(), new TypeReference<>() {});
            for (String q : qs) {
                if (q != null && !q.isBlank()) {
                    aggregated.add(q.trim());
                }
            }
        } catch (JsonProcessingException ignored) {
            // ignore
        }
    }

    private static void mergeSynthFollowUps(Map<String, Object> synthesisSpread, List<String> aggregated) {
        if (synthesisSpread == null) {
            return;
        }
        Object raw = synthesisSpread.get("planningFollowUpQuestionsJson");
        if (raw == null) {
            return;
        }
        try {
            List<String> qs = JSON.readValue(raw.toString(), new TypeReference<>() {});
            for (String q : qs) {
                if (q != null && !q.isBlank()) {
                    aggregated.add(q.trim());
                }
            }
        } catch (JsonProcessingException ignored) {
            // ignore
        }
    }

    private void runRole(
            PlanningCoordinatorRole role,
            FeaturePlanState plan,
            WorkProfileDefinition profile,
            Event event,
            Map<String, Object> state,
            Map<String, Object> spread,
            List<String> aggregatedFollowUps,
            List<String> roleRoundTags) {
        spread.put("planningPhase", "CRITIQUING");
        RolePassResult r =
                PlanningRolePassRunner.run(
                        role, openAiChatClient, plan, profile, event, state, planStateStore, workProfileRegistry);
        if (!r.followUps().isEmpty()) {
            aggregatedFollowUps.addAll(r.followUps());
        }
        String label = roleUserLabel(role);
        if (r.skipped() && "NO_API_KEY".equals(r.error())) {
            roleRoundTags.add("SKIP_NO_KEY");
            spread.put("planningRolePassLastError", label + ": skipped (no API key)");
        } else if (r.skipped()) {
            String reason = r.error() != null ? r.error() : "skipped";
            roleRoundTags.add("SKIP_OTHER:" + label + ": " + truncateOneLine(reason, 80));
            spread.put("planningRolePassLastError", label + ": skipped (" + truncateOneLine(reason, 100) + ")");
        } else if (r.error() != null && looksLikeInterruptedLlmError(r.error())) {
            roleRoundTags.add("INTERRUPTED:" + label);
            spread.put(PLANNING_PASS_INTERRUPTED_KEY, "true");
            spread.put("planningRolePassLastError", label + ": interrupted");
        } else if (r.error() != null
                && r.error().startsWith(StructuredLlmArtifactUpsertPass.STRUCTURED_JSON_PARSE_PREFIX)) {
            roleRoundTags.add("PARSE_ERR:" + label);
            spread.put(PLANNING_STRUCTURED_PASS_PARSE_FAILED_KEY, "true");
            String detail =
                    r.error()
                            .substring(StructuredLlmArtifactUpsertPass.STRUCTURED_JSON_PARSE_PREFIX.length())
                            .trim();
            spread.put(
                    "planningRolePassLastError",
                    label + ": invalid structured JSON — " + truncateOneLine(detail, 120));
        } else if (r.error() != null && !r.error().isBlank()) {
            spread.put("planningRolePassLastError", role.name() + ": " + r.error());
            roleRoundTags.add("ERR:" + label + ": " + truncateOneLine(r.error(), 100));
        } else {
            roleRoundTags.add("OK:" + label);
        }
    }

    private static String summarizeRoleRound(List<PlanningCoordinatorRole> order, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "Role passes not run.";
        }
        if (tags.size() >= 3 && tags.stream().allMatch("SKIP_NO_KEY"::equals)) {
            return "Coordinator passes skipped (OpenAI API key not configured).";
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < tags.size(); i++) {
            String t = tags.get(i);
            String who = i < order.size() ? roleUserLabel(order.get(i)) : "Role";
            if ("SKIP_NO_KEY".equals(t)) {
                parts.add(who + " skipped (no API key)");
            } else if (t != null && t.startsWith("SKIP_OTHER:")) {
                parts.add(t.substring("SKIP_OTHER:".length()).trim());
            } else if (t != null && t.startsWith("INTERRUPTED:")) {
                parts.add(t.substring("INTERRUPTED:".length()).trim() + " interrupted");
            } else if (t != null && t.startsWith("PARSE_ERR:")) {
                parts.add(t.substring("PARSE_ERR:".length()).trim() + " invalid JSON");
            } else if (t != null && t.startsWith("ERR:")) {
                parts.add(t.substring(4).trim());
            } else if (t != null && t.startsWith("OK:")) {
                parts.add(who + " ok");
            } else {
                parts.add(t != null ? t : who);
            }
        }
        return String.join("; ", parts);
    }

    private static String roleUserLabel(PlanningCoordinatorRole role) {
        return switch (role) {
            case COORDINATOR -> "Coordinator";
        };
    }

    private static FeaturePlanState appendAssumption(FeaturePlanState plan, String text) {
        if (text == null || text.isBlank()) {
            return plan;
        }
        String id = "asm-auto-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return plan.withAppendedAssumption(PlanAssumption.fromLegacyText(id, text.trim(), null));
    }

    private static String buildInnerRoundProgressSummary(
            int oneBased, int total, boolean depthOk, String roleRoundSummary) {
        String rs = truncateOneLine(roleRoundSummary != null ? roleRoundSummary : "", 100);
        String tail = depthOk ? "depth gate passed." : "tightening draft.";
        return "Planning inner round "
                + oneBased
                + "/"
                + total
                + " — "
                + tail
                + (rs.isBlank() ? "" : " " + rs);
    }

    private static String buildCycleProgressSummary(
            int cycleIteration,
            boolean depthOk,
            String depthReason,
            RankedClarification ranked,
            boolean userInputRequired,
            String cycleError,
            String rolePassError,
            String roleRoundSummary,
            String synthesisNote,
            String planningLlmError,
            String expansionFallbackUsed,
            String planningLlmSkipReason,
            String selectiveRerunNote,
            boolean planningPassInterrupted,
            boolean structuredParseFailed) {
        StringBuilder sb = new StringBuilder();
        if (selectiveRerunNote != null && !selectiveRerunNote.isBlank()) {
            sb.append(selectiveRerunNote.trim()).append(' ');
        }
        if (planningPassInterrupted) {
            sb.append("A coordinator LLM call was interrupted; retry when ready. ");
        }
        if (structuredParseFailed) {
            sb.append("A drafting step returned output we could not apply; another pass may clear it. ");
        }
        sb.append(truncateOneLine(roleRoundSummary != null ? roleRoundSummary : "", 200)).append(' ');
        if (synthesisNote != null && !synthesisNote.isBlank()) {
            sb.append(truncateOneLine(synthesisNote, 120)).append(' ');
        }
        if ("true".equalsIgnoreCase(expansionFallbackUsed)) {
            sb.append("Used a deterministic fallback for exploration this pass. ");
        }
        if (rolePassError != null && !rolePassError.isBlank()) {
            sb.append(truncateOneLine(rolePassError, 120)).append(' ');
        }
        if (planningLlmError != null && !planningLlmError.isBlank()) {
            sb.append(
                    truncateOneLine(
                            PlanningUserFacingCopy.humanizePlanningCycleFailureFragment(planningLlmError), 120))
                    .append(' ');
        } else if (planningLlmSkipReason != null
                && !planningLlmSkipReason.isBlank()
                && !"MISSING_DEPS".equals(planningLlmSkipReason)) {
            sb.append(
                    truncateOneLine(
                            PlanningUserFacingCopy.humanizePlanningCycleFailureFragment(
                                    "Synthesis skipped: " + planningLlmSkipReason),
                            100))
                    .append(' ');
        }
        if (userInputRequired) {
            sb.append("Waiting on your reply to the question below before continuing.");
        } else if (structuredParseFailed) {
            sb.append("Holding for another drafting pass — the last output could not be applied.");
        } else if (depthOk) {
            sb.append(
                    "Draft looks solid enough to move forward. **Next:** I post the full planning packet in this thread, then run automated readiness checks (about a minute).");
        } else {
            sb.append("Still tightening the draft: ")
                    .append(truncateOneLine(depthReason != null ? depthReason : "details pending", 140));
        }
        if (cycleError != null && !cycleError.isBlank()) {
            sb.append(" Issue: ").append(truncateOneLine(cycleError, 120));
        }
        return sb.toString().trim();
    }

    private static boolean looksLikeInterruptedLlmError(String error) {
        if (error == null || error.isBlank()) {
            return false;
        }
        String t = error.trim();
        if ("ERROR: interrupted".equalsIgnoreCase(t)) {
            return true;
        }
        String lower = t.toLowerCase();
        return lower.contains("interruptedexception") || lower.contains("thread was interrupted");
    }

    private static String truncateOneLine(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\r\n", " ").replace("\n", " ").trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    public static String buildOrchestratorSummary(
            FeaturePlanState plan,
            boolean depthOk,
            String depthReason,
            RankedClarification ranked,
            int cycleIteration,
            boolean readyToPostPacket,
            boolean clarificationStuck,
            String stuckHint,
            boolean userInputRequired,
            boolean structuredParseFailed) {
        return buildOrchestratorSummary(
                plan,
                depthOk,
                depthReason,
                ranked,
                cycleIteration,
                readyToPostPacket,
                clarificationStuck,
                stuckHint,
                userInputRequired,
                structuredParseFailed,
                null);
    }

    public static String buildOrchestratorSummary(
            FeaturePlanState plan,
            boolean depthOk,
            String depthReason,
            RankedClarification ranked,
            int cycleIteration,
            boolean readyToPostPacket,
            boolean clarificationStuck,
            String stuckHint,
            boolean userInputRequired,
            boolean structuredParseFailed,
            String planningRepoEvidenceJson) {
        String req = plan.getInitialRequest() != null ? plan.getInitialRequest().trim() : "";
        String gist = req.length() > 200 ? req.substring(0, 199) + "…" : req;
        String arch = PlanningArtifactTexts.artifactField(plan, "architecture_notes", "impact", "components_impacted");
        StringBuilder sb = new StringBuilder();
        if (clarificationStuck && stuckHint != null && !stuckHint.isBlank()) {
            sb.append(stuckHint).append("\n\n");
        }
        sb.append("**What I'm tracking:** ");
        if (gist.isBlank()) {
            sb.append("your request from this thread.");
        } else {
            sb.append(gist);
        }
        sb.append(workspaceSummaryLine(plan));
        String repoDigest = PlanningThreadPacketFormatter.summarizeRepoEvidenceForHumans(planningRepoEvidenceJson);
        if (!repoDigest.isBlank()) {
            sb.append("\n\n**Repo signals:**\n").append(repoDigest);
        }

        sb.append("\n\n**Draft:** ");
        if (userInputRequired) {
            sb.append("Paused until the detail below is answered.");
        } else if (readyToPostPacket) {
            sb.append(
                    "Ready to drop the full write-up in this thread for your review. Next I'll post the packet and run readiness checks. "
                            + "(Several messages if the draft is long, then automated checks — usually about a minute.)");
        } else if (structuredParseFailed) {
            sb.append("The last drafting pass could not be applied cleanly; another pass should retry it.");
        } else if (depthOk) {
            sb.append("In good shape; I'll keep going or post when the next step runs.");
        } else {
            sb.append("Still polishing (").append(depthReason).append("). ");
            sb.append("I'll keep iterating unless I need a human call on something specific.");
        }
        if (!arch.isBlank()) {
            sb.append("\n\n**Likely touchpoints:** ")
                    .append(arch.length() > 200 ? arch.substring(0, 199) + "…" : arch);
        }

        if (!ranked.assumptionsToRecord().isEmpty()) {
            sb.append("\n\n**Noted this round:**\n");
            for (String a : ranked.assumptionsToRecord()) {
                sb.append("- ").append(a).append("\n");
            }
        }

        if (userInputRequired) {
            String q = ranked.questionText() != null ? ranked.questionText().trim() : "";
            sb.append("\n**Need from you:** ");
            if (!q.isBlank()) {
                sb.append(q).append("\n\n");
            }
            if (ranked.useStructuredChoices()) {
                sb.append("Use the choices below if shown, or **Use recommended default**.");
            } else {
                sb.append("Reply in plain text in this thread.");
            }
        }
        return sb.toString().trim();
    }

    private static String workspaceSummaryLine(FeaturePlanState plan) {
        if (plan == null) {
            return "";
        }
        String path = plan.getRepoLocalPath() != null ? plan.getRepoLocalPath().trim() : "";
        String st = plan.getRepoWorkspaceStatus() != null ? plan.getRepoWorkspaceStatus().trim() : "";
        if (path.isBlank() && st.isBlank()) {
            return "";
        }
        StringBuilder w = new StringBuilder();
        w.append("\n**Workspace:** ");
        if (!st.isBlank()) {
            w.append(PlanningDraftSupport.humanizeRepoWorkspaceStatus(st));
        }
        if (!path.isBlank()) {
            if (!st.isBlank()) {
                w.append("; ");
            }
            w.append("local path `").append(path.length() > 120 ? path.substring(0, 119) + "…" : path).append("`");
        }
        w.append(".");
        return w.toString();
    }

    public static String normalizeClarificationQuestion(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.toLowerCase().replaceAll("\\s+", " ").trim();
        return t.replaceAll("[^a-z0-9?\\s]", "");
    }

    private static Map<String, Object> baseSpread() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planningRoomCycleError", "");
        m.put("planningPhase", "");
        m.put("planningUserInputRequired", "false");
        m.put("planningCanonicalUserInputRequired", "false");
        m.put("planningLlmUserInputSuggested", "false");
        m.put("planningClarificationChoicesJson", "[]");
        m.put("planningClarificationMetaJson", "{}");
        m.put("planningOrchestratorRoundSummary", "");
        m.put("planningPacketDepthOk", "false");
        m.put("planningPacketDepthReason", "");
        m.put("planningPacketDepthRetryRecommended", "false");
        m.put("planningReadyToPostPacket", "false");
        m.put(PlanningReadinessSpread.PACKET_POSTING_ALLOWED_KEY, "false");
        m.put(PlanningReadinessSpread.REVIEW_ALLOWED_KEY, "false");
        m.put(PlanningReadinessSpread.APPROVAL_ALLOWED_KEY, "false");
        m.put("planningRevisionNeeded", "false");
        m.put("planningQuestionsAskedThisRound", "0");
        m.put("planningBlockingQuestionCount", "0");
        m.put("planningAssumptionsUsed", "0");
        m.put("planningRolePassLastError", "");
        m.put(PLANNING_PASS_INTERRUPTED_KEY, "false");
        m.put(PLANNING_STRUCTURED_PASS_PARSE_FAILED_KEY, "false");
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
        m.put(PlanningPostDraftGovernor.SPREAD_KEY, PlanningPostDraftAction.BLOCK.name());
        m.put(PlanningPostDraftGovernor.NOTICE_MARKDOWN_KEY, "");
        m.put(PlanningPostDraftGovernor.BASELINE_REPO_HASH_KEY, "");
        m.put(PlanningPostDraftGovernor.BASELINE_ASSUMPTION_COUNT_KEY, "");
        m.put(PlanningPostDraftGovernor.BASELINE_CRITIQUE_BLOCKING_KEY, "");
        m.put(PlanningPostDraftGovernor.BASELINE_DRAFT_FP_KEY, "");
        m.put(PlanningPostDraftGovernor.LAST_REVISION_SITUATION_KEY, "");
        return m;
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

    private static String blankToEmpty(String value) {
        return value != null && !value.isBlank() ? value : "";
    }
}
