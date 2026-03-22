package com.vinekeepers.workflow.planning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.CoordinatorClarificationSettings;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.AssumptionEntry;
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
import com.vinekeepers.workflow.planning.PlanningQuestionRankingPolicy.RankedClarification;
import com.vinekeepers.workflow.planning.PlanningRolePassRunner.RolePassResult;
import com.vinekeepers.workflow.planreview.PlanningArtifactTexts;
import com.vinekeepers.workflow.planreview.PlanningPacketDepthEvaluator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            PlanningDeliberationLedgerSync.UpsertResult upsert) {}

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
                    "Selective rerun: skipped request-expansion; running one critique round after your clarification.");
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
            if (depthOk) {
                break;
            }
        }

        spread.put("planningCycleRolePassSummary", lastRoleRoundSummary);
        spread.put("planningCycleSynthesisLlmNote", lastSynthLlmLine);

        plan = planStateStore.getByContextId(contextId).orElse(planPtr);
        ClarificationRoundOutcome clr = resolveClarificationRound(contextId, plan, state, profile, aggregatedFollowUps);
        plan = clr.plan();
        RankedClarification rankedLlm = clr.rankedLlm();
        PlanningDeliberationLedgerSync.UpsertResult upsert = clr.upsert();
        UnresolvedItemLedger.mergeLedgerIntoSpread(spread, upsert.ledger());
        spread.put("planningClarificationLedgerItemId", upsert.activeItemId().orElse(""));

        CoordinatorClarificationSettings coord = profile.getCoordinatorClarification();
        RankedClarification ranked =
                PlanningGapEvaluator.effectiveRanked(plan, upsert.ledger(), rankedLlm, profile, coord);
        boolean userInputRequired = PlanningGapEvaluator.requiresUserInputForPlanningClarification(upsert.ledger());

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
        boolean readyToPost = depthOk && !userInputRequired;
        applyClarificationStuck(state, spread, ranked);

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
                        readyToPost,
                        clarificationStuck,
                        stuckHint != null ? stuckHint : ""));
        spread.put(
                "planningRevisionNeeded",
                (!depthOk || userInputRequired) ? "true" : "false");

        spread.put("planningReadyToPostPacket", readyToPost ? "true" : "false");
        spread.put(
                "planningPhase",
                userInputRequired
                        ? "WAITING_FOR_CLARIFICATION"
                        : (readyToPost ? "READY_FOR_REVIEW" : "REVISING"));
        PlanningReadinessSpread.applyCycleReadiness(spread, readyToPost, userInputRequired);
        DeliberationEngine.applyDerivedDeliberationSpread(spread);
        spread.put("planningAssumptionsUsed", String.valueOf(rankedLlm.assumptionsToRecord().size()));

        if (!readyToPost && !userInputRequired && cycleIteration >= 4) {
            spread.put("planningRoomCycleError", "DEPTH_FAIL_AFTER_RETRIES: " + depthReason);
        }
        String rolePassErr =
                spread.get("planningRolePassLastError") != null
                        ? spread.get("planningRolePassLastError").toString()
                        : "";
        String llmErr = firstNonBlank(getString(spread, "planningLlmError"), "");
        spread.put(
                "planningCycleUserVisibleFailure",
                buildUserVisibleFailure(
                        spread.get("planningRoomCycleError") != null
                                ? spread.get("planningRoomCycleError").toString()
                                : "",
                        llmErr));

        spread.put(
                "planningCycleProgressSummary",
                buildCycleProgressSummary(
                        cycleIteration,
                        depthOk,
                        depthReason,
                        ranked,
                        spread.get("planningRoomCycleError") != null
                                ? spread.get("planningRoomCycleError").toString()
                                : "",
                        rolePassErr,
                        lastRoleRoundSummary,
                        lastSynthLlmLine,
                        llmErr,
                        getString(spread, "planningExpansionFallbackUsed"),
                        getString(spread, "planningLlmSkipReason"),
                        getString(spread, "planningSelectiveRerunNote")));

        enrichUserCopyAndProgressLog(state, spread);
        spread.put("planningJustMergedClarification", "false");
        spread.put("planningSelectiveRerunActive", "false");
        finishProgressFingerprint(state, spread);
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
     * Partial cycle: one architect/auditor/scribe round, expand drafts, synthesis, pre-critique, depth check.
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
                resolveClarificationRound(contextId, plan, state, profileFin, aggregatedFollowUps);
        plan = clrFin.plan();
        RankedClarification rankedLlmFin = clrFin.rankedLlm();
        PlanningDeliberationLedgerSync.UpsertResult upsertFin = clrFin.upsert();
        UnresolvedItemLedger.mergeLedgerIntoSpread(spread, upsertFin.ledger());
        spread.put("planningClarificationLedgerItemId", upsertFin.activeItemId().orElse(""));

        CoordinatorClarificationSettings coordFin = profileFin.getCoordinatorClarification();
        RankedClarification ranked =
                PlanningGapEvaluator.effectiveRanked(plan, upsertFin.ledger(), rankedLlmFin, profileFin, coordFin);
        boolean userInputRequired = PlanningGapEvaluator.requiresUserInputForPlanningClarification(upsertFin.ledger());

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
        boolean readyToPost = depthOk && !userInputRequired;
        applyClarificationStuck(state, spread, ranked);

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
                        readyToPost,
                        clarificationStuck,
                        stuckHint != null ? stuckHint : ""));
        spread.put(
                "planningRevisionNeeded",
                (!depthOk || userInputRequired) ? "true" : "false");

        spread.put("planningReadyToPostPacket", readyToPost ? "true" : "false");
        spread.put(
                "planningPhase",
                userInputRequired
                        ? "WAITING_FOR_CLARIFICATION"
                        : (readyToPost ? "READY_FOR_REVIEW" : "REVISING"));
        PlanningReadinessSpread.applyCycleReadiness(spread, readyToPost, userInputRequired);
        DeliberationEngine.applyDerivedDeliberationSpread(spread);
        spread.put("planningAssumptionsUsed", String.valueOf(rankedLlmFin.assumptionsToRecord().size()));

        if (!readyToPost && !userInputRequired && cycleIteration >= 4) {
            spread.put("planningRoomCycleError", "DEPTH_FAIL_AFTER_RETRIES: " + depthReason);
        }
        String rolePassErr =
                spread.get("planningRolePassLastError") != null
                        ? spread.get("planningRolePassLastError").toString()
                        : "";
        String llmErr = firstNonBlank(getString(spread, "planningLlmError"), "");
        spread.put(
                "planningCycleUserVisibleFailure",
                buildUserVisibleFailure(
                        spread.get("planningRoomCycleError") != null
                                ? spread.get("planningRoomCycleError").toString()
                                : "",
                        llmErr));

        spread.put(
                "planningCycleProgressSummary",
                buildCycleProgressSummary(
                        cycleIteration,
                        depthOk,
                        depthReason,
                        ranked,
                        spread.get("planningRoomCycleError") != null
                                ? spread.get("planningRoomCycleError").toString()
                                : "",
                        rolePassErr,
                        lastRoleRoundSummary,
                        lastSynthLlmLine,
                        llmErr,
                        getString(spread, "planningExpansionFallbackUsed"),
                        getString(spread, "planningLlmSkipReason"),
                        ""));

        enrichUserCopyAndProgressLog(state, spread);
        finishProgressFingerprint(state, spread);
        return spread;
    }

    private ClarificationRoundOutcome resolveClarificationRound(
            String contextId,
            FeaturePlanState plan,
            Map<String, Object> state,
            WorkProfileDefinition profile,
            List<String> aggregatedFollowUps) {
        UnresolvedItemLedger ledger = UnresolvedItemLedger.readFrom(state);
        CoordinatorClarificationSettings coord = profile.getCoordinatorClarification();
        RankedClarification rankedLlm;
        if (coord.isCanonicalV1()) {
            List<CoordinatorClarificationGapEvaluator.OpenGap> openA =
                    CoordinatorClarificationGapEvaluator.evaluateOpenGaps(plan, coord, aggregatedFollowUps);
            ledger =
                    PlanningDeliberationLedgerSync.reconcileCanonicalOpenGaps(
                            ledger, CoordinatorClarificationGapEvaluator.openGapIds(openA));
            if (openA.isEmpty()) {
                rankedLlm = emptyRankedClarification();
            } else {
                CoordinatorClarificationGapEvaluator.OpenGap top = openA.get(0);
                rankedLlm =
                        PlanningQuestionRankingPolicy.rank(
                                plan,
                                List.of(top.questionText()),
                                3,
                                ledger,
                                profile.isBoundedClarificationChoicesEnabled(),
                                profile.isInferBoundedChoiceFromOrInTextEnabled());
                rankedLlm = withCoordinatorGapMeta(rankedLlm, top.gapId(), top.blocking());
            }
            for (String assumption : rankedLlm.assumptionsToRecord()) {
                plan = appendAssumption(plan, assumption);
            }
            planStateStore.update(plan);
            plan = planStateStore.getByContextId(contextId).orElse(plan);

            List<CoordinatorClarificationGapEvaluator.OpenGap> openB =
                    CoordinatorClarificationGapEvaluator.evaluateOpenGaps(plan, coord, aggregatedFollowUps);
            ledger =
                    PlanningDeliberationLedgerSync.reconcileCanonicalOpenGaps(
                            ledger, CoordinatorClarificationGapEvaluator.openGapIds(openB));
            if (openB.isEmpty()) {
                rankedLlm = emptyRankedClarification();
            } else {
                CoordinatorClarificationGapEvaluator.OpenGap top = openB.get(0);
                rankedLlm =
                        PlanningQuestionRankingPolicy.rank(
                                plan,
                                List.of(top.questionText()),
                                3,
                                ledger,
                                profile.isBoundedClarificationChoicesEnabled(),
                                profile.isInferBoundedChoiceFromOrInTextEnabled());
                rankedLlm = withCoordinatorGapMeta(rankedLlm, top.gapId(), top.blocking());
            }
            for (String assumption : rankedLlm.assumptionsToRecord()) {
                plan = appendAssumption(plan, assumption);
            }
            planStateStore.update(plan);
            plan = planStateStore.getByContextId(contextId).orElse(plan);

            List<CoordinatorClarificationGapEvaluator.OpenGap> openFinal =
                    CoordinatorClarificationGapEvaluator.evaluateOpenGaps(plan, coord, aggregatedFollowUps);
            ledger =
                    PlanningDeliberationLedgerSync.reconcileCanonicalOpenGaps(
                            ledger, CoordinatorClarificationGapEvaluator.openGapIds(openFinal));
            if (openFinal.isEmpty()) {
                rankedLlm = emptyRankedClarification();
            } else {
                CoordinatorClarificationGapEvaluator.OpenGap top = openFinal.get(0);
                rankedLlm =
                        PlanningQuestionRankingPolicy.rank(
                                plan,
                                List.of(top.questionText()),
                                3,
                                ledger,
                                profile.isBoundedClarificationChoicesEnabled(),
                                profile.isInferBoundedChoiceFromOrInTextEnabled());
                rankedLlm = withCoordinatorGapMeta(rankedLlm, top.gapId(), top.blocking());
            }
            PlanningDeliberationLedgerSync.UpsertResult upsert;
            if (openFinal.isEmpty()) {
                upsert = PlanningDeliberationLedgerSync.upsertOpenQuestion(ledger, rankedLlm);
            } else {
                CoordinatorClarificationGapEvaluator.OpenGap top = openFinal.get(0);
                upsert =
                        PlanningDeliberationLedgerSync.upsertOpenQuestionForCanonicalGap(
                                ledger, rankedLlm, top.gapId(), top.blocking());
            }
            return new ClarificationRoundOutcome(plan, rankedLlm, upsert);
        }
        rankedLlm =
                PlanningQuestionRankingPolicy.rank(
                        plan,
                        aggregatedFollowUps,
                        3,
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
        return new ClarificationRoundOutcome(plan, rankedLlm, upsert);
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

    private LoadedCycle loadCycleOrAbort(
            Event event, Map<String, Object> state, Map<String, Object> bind, Map<String, Object> spread) {
        if (planStateStore == null || workProfileRegistry == null) {
            spread.put("planningRoomCycleError", "MISSING_DEPS");
            return null;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            spread.put("planningRoomCycleError", "NO_CONTEXT");
            return null;
        }
        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            spread.put("planningRoomCycleError", "NO_PLAN");
            return null;
        }
        String profileId = plan.getProfileId();
        WorkProfileDefinition profile =
                profileId != null && !profileId.isBlank()
                        ? workProfileRegistry.get(profileId).orElse(null)
                        : null;
        if (profile == null || profile.findSection("request_exploration", "analysis").isEmpty()) {
            spread.put("planningRoomCycleError", "PROFILE_NOT_V2");
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
        String lastSynthLlmLine = pickSynthLlmLine(synthSpread, previousSynthLine);

        new SynthesizePreCritiqueArtifactsAction(planStateStore, workProfileRegistry).run(event, work, bind);

        planPtr = planStateStore.getByContextId(contextId).orElse(planPtr);
        PlanningPacketDepthEvaluator.DepthResult dr = PlanningPacketDepthEvaluator.evaluate(planPtr, profile);
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
        String fullForHash = ProgressDedupeHelper.normalizeProgressBody("**Planning cycle** — " + summary);
        String fp = ProgressDedupeHelper.fingerprintForBody(fullForHash);
        spread.put("planningProgressPostFingerprint", fp);
        String last = state != null ? getString(state, "planningLastProgressPostHash") : null;
        boolean worthy = ProgressDedupeHelper.isPostWorthy(last, fp);
        spread.put("planningProgressPostWorthy", worthy ? "true" : "false");
    }

    private static String buildUserVisibleFailure(String cycleError, String planningLlmError) {
        StringBuilder sb = new StringBuilder();
        if (cycleError != null && !cycleError.isBlank()) {
            sb.append(truncateOneLine(cycleError, 200));
        }
        if (planningLlmError != null && !planningLlmError.isBlank()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(truncateOneLine(planningLlmError, 200));
        }
        return sb.toString().trim();
    }

    private static void applyClarificationStuck(
            Map<String, Object> state, Map<String, Object> spread, RankedClarification ranked) {
        String prevQ = normalizeClarificationQuestion(getString(state, "planningPreviousClarificationQuestionText"));
        String currQ = normalizeClarificationQuestion(ranked.questionText());
        boolean sameAsPrev = ranked.userInputRequired() && !prevQ.isEmpty() && currQ.equals(prevQ);
        int prevCount = parseInt(getString(state, "planningClarificationRepeatCount"), 0);
        int newCount = sameAsPrev ? prevCount + 1 : 0;
        spread.put("planningClarificationRepeatCount", String.valueOf(newCount));
        boolean stuck = sameAsPrev && newCount >= 1;
        spread.put("planningClarificationStuck", stuck ? "true" : "false");
        String lid = getString(spread, "planningClarificationLedgerItemId");
        String lidNote =
                lid != null && !lid.isBlank() ? " Ledger item `" + lid + "` is still open — " : " ";
        spread.put(
                "planningClarificationStuckHint",
                stuck
                        ? "We already recorded your answer, but the draft still surfaces the same blocking question."
                                + lidNote
                                + "Reply with a concrete example or constraint, use **Use recommended default** if shown, "
                                + "or use the coordinator menu to add scope or reframe the request."
                        : "");
    }

    private static String pickSynthLlmLine(Map<String, Object> synthSpread, String previous) {
        if (synthSpread == null) {
            return previous;
        }
        String err = getString(synthSpread, "planningLlmError");
        if (err != null && !err.isBlank()) {
            return truncateOneLine(err, 140);
        }
        String skip = getString(synthSpread, "planningLlmSkipReason");
        if (skip != null && !skip.isBlank() && !"OK".equalsIgnoreCase(skip)) {
            return "Synthesis skipped: " + truncateOneLine(skip, 120);
        }
        return previous;
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
            case ARCHITECT -> "Architect";
            case AUDITOR -> "Auditor";
            case SCRIBE -> "Scribe";
        };
    }

    private static FeaturePlanState appendAssumption(FeaturePlanState plan, String text) {
        if (text == null || text.isBlank()) {
            return plan;
        }
        String id = "asm-auto-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return plan.withAppendedAssumption(new AssumptionEntry(id, text.trim(), null));
    }

    private static String buildCycleProgressSummary(
            int cycleIteration,
            boolean depthOk,
            String depthReason,
            RankedClarification ranked,
            String cycleError,
            String rolePassError,
            String roleRoundSummary,
            String synthesisNote,
            String planningLlmError,
            String expansionFallbackUsed,
            String planningLlmSkipReason,
            String selectiveRerunNote) {
        StringBuilder sb = new StringBuilder();
        if (selectiveRerunNote != null && !selectiveRerunNote.isBlank()) {
            sb.append(selectiveRerunNote.trim()).append(' ');
        }
        sb.append("Status (cycle ").append(cycleIteration).append("): ");
        sb.append(roleRoundSummary != null ? roleRoundSummary : "").append(' ');
        if (synthesisNote != null && !synthesisNote.isBlank()) {
            sb.append("Synthesis: ").append(truncateOneLine(synthesisNote, 120)).append(' ');
        }
        if ("true".equalsIgnoreCase(expansionFallbackUsed)) {
            sb.append("Exploration expansion used deterministic fallback. ");
        }
        if (rolePassError != null && !rolePassError.isBlank()) {
            sb.append("Pass note: ").append(truncateOneLine(rolePassError, 120)).append(' ');
        }
        if (planningLlmError != null && planningLlmError.startsWith("ERROR:")) {
            sb.append("LLM: ").append(truncateOneLine(planningLlmError, 120)).append(' ');
        } else if (planningLlmSkipReason != null
                && !planningLlmSkipReason.isBlank()
                && !"MISSING_DEPS".equals(planningLlmSkipReason)) {
            sb.append("Synthesis skip: ").append(truncateOneLine(planningLlmSkipReason, 100)).append(' ');
        }
        if (ranked.userInputRequired()) {
            sb.append("I need one answer before I can finish the draft and post the packet.");
        } else if (depthOk) {
            sb.append("Depth check passed.");
        } else {
            sb.append("Draft still being strengthened: ")
                    .append(truncateOneLine(depthReason != null ? depthReason : "details pending", 140));
        }
        if (cycleError != null && !cycleError.isBlank()) {
            sb.append(" Issue: ").append(truncateOneLine(cycleError, 120));
        }
        return sb.toString().trim();
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
            String stuckHint) {
        String req = plan.getInitialRequest() != null ? plan.getInitialRequest().trim() : "";
        String gist = req.length() > 200 ? req.substring(0, 199) + "…" : req;
        String arch = PlanningArtifactTexts.artifactField(plan, "architecture_notes", "impact", "components_impacted");
        StringBuilder sb = new StringBuilder();
        if (clarificationStuck && stuckHint != null && !stuckHint.isBlank()) {
            sb.append("**Heads up:** ").append(stuckHint).append("\n\n");
        }
        sb.append("**Planning update (cycle ").append(cycleIteration).append(")**\n\n");
        sb.append("**Here's my current understanding:** ");
        if (gist.isBlank()) {
            sb.append("Your feature request from this thread (no separate summary text on file).");
        } else {
            sb.append(gist);
        }
        sb.append(workspaceSummaryLine(plan));

        sb.append("\n\n**Where the draft stands:** ");
        if (ranked.userInputRequired()) {
            sb.append(
                    "The coordinator passes have run on the current draft, but we are **not** posting the planning packet yet until we resolve the clarification below.");
        } else if (readyToPostPacket) {
            sb.append("Depth check passed; the planning packet is ready to post in this thread for review.");
        } else if (depthOk) {
            sb.append("Depth check passed; the next steps will post or refine the packet.");
        } else {
            sb.append("Still strengthening the draft (").append(depthReason).append("). ");
            sb.append("We will retry automatically inside this round or ask you only when something needs a human decision.");
        }
        if (!arch.isBlank()) {
            sb.append("\n\n**I think this will mainly touch:** ")
                    .append(arch.length() > 200 ? arch.substring(0, 199) + "…" : arch);
        }

        if (!ranked.assumptionsToRecord().isEmpty()) {
            sb.append("\n\n**Assumptions recorded this round (auto):**\n");
            for (String a : ranked.assumptionsToRecord()) {
                sb.append("- ").append(a).append("\n");
            }
        }

        if (ranked.userInputRequired()) {
            String q = ranked.questionText() != null ? ranked.questionText().trim() : "";
            sb.append("\n**I need one answer before I can finish the draft:**\n\n");
            if (!q.isBlank()) {
                sb.append(q).append("\n\n");
            }
            sb.append("**Why this matters:** ");
            if (ranked.blockingQuestionCount() > 0) {
                sb.append(
                        "Your answer affects compatibility, security, migrations, or validation — getting it wrong would be expensive to unwind during implementation.\n\n");
            } else {
                sb.append(
                        "This shapes scope and design choices in the packet so implementation matches what you expect.\n\n");
            }
            sb.append(
                    "**Next I'll:** refresh exploration, run the three coordinator passes, check depth, then post the packet or ask again if something is still ambiguous.\n");
            if (ranked.useStructuredChoices()) {
                sb.append(
                        "\nChoose an option below, or pick **Use recommended default** to record our baseline and continue.");
            } else {
                sb.append("\n**Reply in plain text** with your answer. You can give examples or edge cases — no need to match a fixed list.");
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
            w.append("status ").append(st);
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
        m.put("planningClarificationChoicesJson", "[]");
        m.put("planningClarificationMetaJson", "{}");
        m.put("planningOrchestratorRoundSummary", "");
        m.put("planningPacketDepthOk", "false");
        m.put("planningPacketDepthReason", "");
        m.put("planningPacketDepthRetryRecommended", "false");
        m.put("planningReadyToPostPacket", "false");
        m.put("planningRevisionNeeded", "false");
        m.put("planningQuestionsAskedThisRound", "0");
        m.put("planningBlockingQuestionCount", "0");
        m.put("planningAssumptionsUsed", "0");
        m.put("planningRolePassLastError", "");
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
}
