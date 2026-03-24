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
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.PlanAssumptionStatus;
import com.vinekeepers.state.planning.PlanConfidence;
import com.vinekeepers.state.planning.PlanGovernanceSeverity;
import com.vinekeepers.state.planning.PlanIssue;
import com.vinekeepers.state.planning.PlanIssueStatus;
import com.vinekeepers.state.workflow.ProgressDedupeHelper;
import com.vinekeepers.state.workflow.ProgressEventLog;
import com.vinekeepers.state.workflow.UnresolvedItem;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.state.workflow.UnresolvedItemStatus;
import com.vinekeepers.workflow.deliberation.DeliberationEngine;
import com.vinekeepers.state.planning.PlanningCanonicalDecision;
import com.vinekeepers.state.planning.PlanningCanonicalNextAction;
import com.vinekeepers.state.planning.PlanningFailureCategory;
import com.vinekeepers.state.planning.PlanningIntakeStage;
import com.vinekeepers.workflow.planreview.PlanningArtifactTexts;
import com.vinekeepers.workflow.planreview.PlanningThreadPacketFormatter;
import com.vinekeepers.workflow.planreview.PlanningUserFacingCopy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Composable planning-room cycle: expansion, role passes, synthesis, depth, clarification spread keys.
 * Invoked by {@link com.vinekeepers.workflow.actions.ExecutePlanningRoomCycleAction} and reusable for v2 capabilities.
 */
public final class PlanningCyclePipeline {

    private static final int MAX_BOT_INNER_ROUNDS = 3;
    private static final ObjectMapper JSON = new ObjectMapper();

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
    public static final String PLANNING_CLARIFICATION_CONFIDENCE_SCORE_KEY = "planningClarificationConfidenceScore";
    public static final String PLANNING_CLARIFICATION_CONFIDENCE_HIGH_KEY = "planningClarificationConfidenceHigh";

    private record LoadedCycle(
            String contextId, FeaturePlanState plan, WorkProfileDefinition profile, Map<String, Object> work) {}

    private record ClarificationRoundOutcome(
            FeaturePlanState plan,
            ClarificationProjection rankedLlm,
            PlanningDeliberationLedgerSync.UpsertResult upsert,
            /** When true, canonical_v1 still has at least one open gap (authoritative for {@code planningUserInputRequired}). */
            boolean canonicalClarificationPending,
            /** True when canonical_v1 will surface one askable question this cycle (canonical gap only, not legacy ranker). */
            boolean llmUserInputSuggested,
            /** Budget exhausted on a blocking coordinator gap with no remaining askable path. */
            boolean hardClarificationBlock,
            /** Human-readable debug note for the active hard clarification block, if any. */
            String hardClarificationBlockReason) {}

    private final OpenAiChatClient openAiChatClient;
    private final FeaturePlanStateStore planStateStore;
    private final WorkProfileRegistry workProfileRegistry;
    private final SilentPlanningSynthesisService silentSynthesis;

    public PlanningCyclePipeline(
            OpenAiChatClient openAiChatClient,
            FeaturePlanStateStore planStateStore,
            WorkProfileRegistry workProfileRegistry) {
        this.openAiChatClient = openAiChatClient;
        this.planStateStore = planStateStore;
        this.workProfileRegistry = workProfileRegistry;
        this.silentSynthesis = new SilentPlanningSynthesisService(openAiChatClient, planStateStore, workProfileRegistry);
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

        PlanningWorkspacePreflightService.PreflightResult workspacePre = PlanningWorkspacePreflightService.evaluate(plan);
        spread.put("planningWorkspacePreflightStatus", workspacePre.status().name());
        if (workspacePre.blocked()) {
            spread.put("planningWorkspaceUserInputRequired", "true");
            spread.put("planningWorkspaceBlockerPrompt", workspacePre.userPrompt());
            putPlanningRoomCycleError(spread, "WORKSPACE_BLOCKED");
            finishProgressFingerprint(state, spread);
            return spread;
        }

        if (!profile.getCoordinatorClarification().isCanonicalV1()) {
            putPlanningRoomCycleError(spread, "PLANNING_REQUIRES_CANONICAL_V1");
            finishProgressFingerprint(state, spread);
            return spread;
        }

        int cycleIteration = parseInt(getString(state, "planningRoomCycleIteration"), 0);
        cycleIteration++;
        spread.put("planningRoomCycleIteration", String.valueOf(cycleIteration));

        boolean selective = selectiveRerunAfterClarificationEnabled(state, bind);
        boolean justMerged = "true".equalsIgnoreCase(getString(state, "planningJustMergedClarification"));
        boolean skipExpansion = selective && justMerged;
        if (!skipExpansion) {
            runExpansionPhase(event, work, spread, bind);
        } else {
            spread.put("planningSelectiveRerunActive", "true");
            spread.put(
                    "planningSelectiveRerunNote",
                    "Thanks — I'm refreshing the draft from your last answer (skipping a full re-scan this pass).");
        }

        String depthReason = "";
        boolean depthOk = false;
        String lastRoleRoundSummary = "";
        String lastSynthLlmLine = "";
        FeaturePlanState planPtr = plan;

        int innerRounds = (selective && justMerged) ? 1 : MAX_BOT_INNER_ROUNDS;
        for (int inner = 0; inner < innerRounds; inner++) {
            SilentPlanningSynthesisService.InnerRoundResult round =
                    silentSynthesis.runSingleInnerRound(
                            event, contextId, profile, work, spread, bind, planPtr, lastSynthLlmLine);
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
                resolveClarificationRound(contextId, plan, state, profile, true, cycleIteration);
        plan = clr.plan();
        ClarificationProjection rankedLlm = clr.rankedLlm();
        PlanningDeliberationLedgerSync.UpsertResult upsert = clr.upsert();
        UnresolvedItemLedger.mergeLedgerIntoSpread(spread, upsert.ledger());
        spread.put("planningClarificationLedgerItemId", upsert.activeItemId().orElse(""));

        ClarificationProjection ranked = rankedLlm;
        boolean userInputRequired = clr.canonicalClarificationPending();

        PlanGovernanceDeriver.deriveAndPersist(planStateStore, contextId, profile);
        plan = planStateStore.getByContextId(contextId).orElse(plan);

        spread.put("planningCanonicalUserInputRequired", clr.canonicalClarificationPending() ? "true" : "false");
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
        applyClarificationConfidence(spread, state, plan, upsert.ledger(), ranked, profile, userInputRequired, depthOk, structuredParseFailed);
        applyClarificationStuck(state, spread, ranked, userInputRequired);

        applyMaterialRoutingAndCanonicalDecision(
                contextId,
                state,
                spread,
                plan,
                clr,
                ranked,
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
     * Partial cycle: request expansion LLM + build request exploration. Bumps {@code planningRoomCycleIteration}.
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
        boolean selective = selectiveRerunAfterClarificationEnabled(state, bind);
        boolean justMerged = "true".equalsIgnoreCase(getString(state, "planningJustMergedClarification"));
        if (selective && justMerged) {
            spread.put("planningSelectiveRerunActive", "true");
            spread.put(
                    "planningSelectiveRerunNote",
                    "Thanks — I'm refreshing the draft from your last answer (skipping a full re-scan this pass).");
        } else {
            runExpansionPhase(event, ctx.work(), spread, bind);
        }
        return spread;
    }

    /**
     * Partial cycle: one Arrietty planning round, expand drafts, synthesis, pre-critique, depth check.
     */
    public Map<String, Object> runInnerRoundOnce(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = baseSpread();
        LoadedCycle ctx = loadCycleOrAbort(event, state, bind, spread);
        if (ctx == null) {
            finishProgressFingerprint(state, spread);
            return spread;
        }
        String contextId = ctx.contextId();
        String prevSynth = getString(state, PARTIAL_LAST_SYNTH_KEY);
        FeaturePlanState plan = ctx.plan();
        SilentPlanningSynthesisService.InnerRoundResult round =
                silentSynthesis.runSingleInnerRound(
                        event,
                        contextId,
                        ctx.profile(),
                        ctx.work(),
                        spread,
                        bind,
                        plan,
                        prevSynth != null ? prevSynth : "");
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
        WorkProfileDefinition profileFin0 = ctx.profile();
        if (!profileFin0.getCoordinatorClarification().isCanonicalV1()) {
            putPlanningRoomCycleError(spread, "PLANNING_REQUIRES_CANONICAL_V1");
            finishProgressFingerprint(state, spread);
            return spread;
        }
        String contextId = ctx.contextId();
        int cycleIteration = parseInt(getString(state, "planningRoomCycleIteration"), 0);
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
                resolveClarificationRound(contextId, plan, state, profileFin, true, cycleIteration);
        plan = clrFin.plan();
        ClarificationProjection rankedLlmFin = clrFin.rankedLlm();
        PlanningDeliberationLedgerSync.UpsertResult upsertFin = clrFin.upsert();
        UnresolvedItemLedger.mergeLedgerIntoSpread(spread, upsertFin.ledger());
        spread.put("planningClarificationLedgerItemId", upsertFin.activeItemId().orElse(""));

        ClarificationProjection ranked = rankedLlmFin;
        boolean userInputRequired = clrFin.canonicalClarificationPending();

        PlanGovernanceDeriver.deriveAndPersist(planStateStore, contextId, profileFin);
        plan = planStateStore.getByContextId(contextId).orElse(plan);

        spread.put("planningCanonicalUserInputRequired", clrFin.canonicalClarificationPending() ? "true" : "false");
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
        applyClarificationConfidence(
                spread, state, plan, upsertFin.ledger(), ranked, profileFin, userInputRequired, depthOk, structuredParseFailedFinalize);
        applyClarificationStuck(state, spread, ranked, userInputRequired);

        applyMaterialRoutingAndCanonicalDecision(
                contextId,
                state,
                spread,
                plan,
                clrFin,
                ranked,
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
                        getString(spread, "planningSelectiveRerunNote"),
                        "true".equalsIgnoreCase(getString(spread, PLANNING_PASS_INTERRUPTED_KEY)),
                        structuredParseFailedFinalize));
        spread.put(
                "userCopyCoordinatorProgress",
                spread.get("planningCycleProgressSummary") != null
                        ? spread.get("planningCycleProgressSummary").toString()
                        : "");

        enrichUserCopyAndProgressLog(state, spread);
        spread.put("planningJustMergedClarification", "false");
        spread.put("planningSelectiveRerunActive", "false");
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
            boolean draftingCompletedThisInvocation,
            int cycleIteration) {
        UnresolvedItemLedger ledger = UnresolvedItemLedger.readFrom(state);
        CoordinatorClarificationSettings coord = profile.getCoordinatorClarification();
        if (!coord.isCanonicalV1()) {
            PlanningDeliberationLedgerSync.UpsertResult upsert =
                    PlanningDeliberationLedgerSync.upsertOpenQuestion(ledger, emptyClarificationProjection());
            return new ClarificationRoundOutcome(
                    plan, emptyClarificationProjection(), upsert, false, false, false, "");
        }
        CanonicalPlanningGapEngine.CanonicalGapDerivationOutcome derived =
                CanonicalPlanningGapEngine.deriveCanonicalGapsAndApplySweeps(
                        contextId, plan, state, profile, draftingCompletedThisInvocation, planStateStore);
        plan = derived.plan();
        ClarificationProjection rankedLlm = emptyClarificationProjection();
        boolean willAsk = false;
        Optional<CanonicalPlanningGap> askOpt = derived.candidateAskGap();
        if (askOpt.isPresent() && !derived.hardClarificationBlock()) {
            CanonicalPlanningGap g = askOpt.get();
            CoordinatorClarificationGapRule rule = coord.findGapRule(g.gapId()).orElse(null);
            int priorAskCount = PlanningGapAskCounts.countForGap(plan.getPlanningGapAskCountsJson(), g.gapId());
            QuestionMode qMode = PlanningQuestionComposer.questionModeForPriorAskCount(priorAskCount);
            String q =
                    PlanningQuestionComposer.composeQuestionForAskCycle(
                            g.questionSeed(), g.gapId(), rule, ledger, priorAskCount);
            if (q.isBlank()) {
                q = g.questionSeed().trim();
            }
            if (q.isBlank()) {
                q =
                        PlanningDeliberationLedgerSync.lastMergedQuestionTextForPlanningGap(ledger, g.gapId())
                                .orElse("")
                                .trim();
            }
            rankedLlm =
                    CanonicalClarificationSpreadBuilder.projectCanonicalPlanningGap(
                            ledger, profile, coord, g, q, List.of(), qMode);
            willAsk =
                    rankedLlm.userInputRequired()
                            && rankedLlm.questionText() != null
                            && !rankedLlm.questionText().isBlank();
        }
        Set<String> allowedOpen;
        List<CoordinatorClarificationGapEvaluator.OpenGap> openRawFinal = derived.rawOpenGaps();
        if (askOpt.isEmpty()) {
            allowedOpen = CoordinatorClarificationGapEvaluator.openGapIds(openRawFinal);
        } else if (willAsk) {
            allowedOpen = Set.of(askOpt.get().gapId());
        } else {
            allowedOpen = Set.of();
        }
        ledger = PlanningDeliberationLedgerSync.reconcileCanonicalOpenGaps(ledger, allowedOpen);
        PlanningDeliberationLedgerSync.UpsertResult upsert;
        if (askOpt.isEmpty()) {
            upsert = PlanningDeliberationLedgerSync.upsertOpenQuestion(ledger, rankedLlm);
        } else {
            CanonicalPlanningGap cg = askOpt.get();
            int nextAskCount = PlanningGapAskCounts.countForGap(plan.getPlanningGapAskCountsJson(), cg.gapId()) + 1;
            upsert =
                    PlanningDeliberationLedgerSync.upsertOpenQuestionForCanonicalGap(
                            ledger,
                            rankedLlm,
                            cg.gapId(),
                            cg.blocking(),
                            nextAskCount,
                            escalationLevelForAsk(nextAskCount - 1));
        }
        if (askOpt.isPresent() && willAsk) {
            CanonicalPlanningGap cg = askOpt.get();
            plan =
                    plan.withClarificationQuestionSurfaced("ask:" + cg.gapId() + ":" + cycleIteration)
                            .withPlanningGapAskCountsJson(
                                    PlanningGapAskCounts.incrementAsk(
                                            plan.getPlanningGapAskCountsJson(), cg.gapId()))
                            .withPlanningIntakeStage(PlanningIntakeStage.CLARIFYING, java.time.Instant.now());
            planStateStore.update(plan);
            plan = planStateStore.getByContextId(contextId).orElse(plan);
        }
        boolean canonicalPending = willAsk;
        return new ClarificationRoundOutcome(
                plan,
                rankedLlm,
                upsert,
                canonicalPending,
                willAsk,
                derived.hardClarificationBlock(),
                derived.hardClarificationBlockReason());
    }

    private static String escalationLevelForAsk(int priorAsksBeforeThisCycle) {
        if (priorAsksBeforeThisCycle >= 2) {
            return "BOUNDED";
        }
        if (priorAsksBeforeThisCycle >= 1) {
            return "NARROW";
        }
        return "OPEN";
    }

    private static ClarificationProjection emptyClarificationProjection() {
        return new ClarificationProjection(false, "", "[]", "{}", 0, List.of(), false, "");
    }

    private void applyMaterialRoutingAndCanonicalDecision(
            String contextId,
            Map<String, Object> persistedSessionState,
            Map<String, Object> spread,
            FeaturePlanState plan,
            ClarificationRoundOutcome clr,
            ClarificationProjection ranked,
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
        PlanningMaterialRoutingOutcome gov =
                PlanningMaterialCyclePacing.canonicalPlanningCycleOutcome(
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
                        clr.hardClarificationBlock(),
                        profile.getCoordinatorClarification().getEnginePolicy(),
                        clr.canonicalClarificationPending());
        spread.put("planningHardClarificationBlockReason", clr.hardClarificationBlockReason());
        if (gov.action() == PlanningPostDraftAction.BLOCK) {
            spread.put("planningUserInputRequired", "false");
            spread.put("planningCanonicalUserInputRequired", "false");
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
        String materialFingerprint = PlanningMaterialFingerprint.materialStateChangeFingerprint(signal, plan);
        PlanningCanonicalDecision canonical =
                PlanningCanonicalDecisionSupport.normalizePostDraftCanonicalV1(
                        plan,
                        gov,
                        clr.canonicalClarificationPending(),
                        ranked,
                        getString(signal, "planningRepoEvidenceJson"),
                        clarificationGapId(ranked),
                        materialFingerprint);
        if (contextId != null && !contextId.isBlank() && plan != null) {
            String askedDecisionId =
                    canonical.nextAction() == PlanningCanonicalNextAction.ASK_ONE_QUESTION ? canonical.decisionId() : "";
            FeaturePlanState updatedPlan =
                    plan.withPlanningCanonicalDecision(canonical, materialFingerprint, askedDecisionId)
                            .withPlanningIntakeStage(canonical.stage(), java.time.Instant.now());
            if (canonical.nextAction() == PlanningCanonicalNextAction.BLOCK) {
                String synthCat = blankToEmpty(getString(spread, "planningSynthesisFailureCategory"));
                if (!synthCat.isBlank()) {
                    boolean rd = "true".equalsIgnoreCase(getString(spread, "planningRecoverableDraftAfterSynthesis"));
                    updatedPlan =
                            updatedPlan.withPlannerRecoveryFields(
                                    PlanningFailureCategory.parse(synthCat),
                                    getString(spread, "planningPhase"),
                                    rd,
                                    PlanningUserFacingCopy.humanizePlanningRoomCycleErrorCode(synthCat));
                }
            }
            planStateStore.update(updatedPlan);
            plan = planStateStore.getByContextId(contextId).orElse(updatedPlan);
        }
        PlanningMaterialRoutingOutcome aligned =
                PlanningMaterialCyclePacing.readinessResultAlignedWithCanonical(canonical, gov);
        boolean effectiveUser = canonical.nextAction() == PlanningCanonicalNextAction.ASK_ONE_QUESTION;
        boolean effectiveReady = canonical.nextAction() == PlanningCanonicalNextAction.POST_PACKET;
        String situation =
                PlanningMaterialFingerprint.revisionSituationFingerprint(
                        depthOk,
                        structuredParseFailed,
                        depthReason != null ? depthReason : "",
                        effectiveUser,
                        effectiveReady,
                        clr.upsert().ledger());
        spread.put("planningReadyToPostPacket", effectiveReady ? "true" : "false");
        spread.put("planningPhase", aligned.planningPhase());
        spread.put("planningRevisionNeeded", aligned.revisionNeeded() ? "true" : "false");
        PlanningReadinessSpread.applyCycleReadiness(spread, aligned);
        PlanningCanonicalDecisionSupport.projectToSpread(spread, canonical);
        spread.put(
                PlanningMaterialSpreadKeys.NOTICE_MARKDOWN_KEY,
                gov.noticeMarkdown() != null ? gov.noticeMarkdown() : "");
        PlanningMaterialCyclePacing.writePersistenceKeys(
                spread, signal, plan, aligned, aligned.revisionNeeded(), situation);
        spread.put(PlanningCanonicalDecisionSupport.LAST_MATERIAL_CHANGE_FP_KEY, materialFingerprint);
        persistConfidenceBreakdown(
                contextId,
                plan,
                spread,
                persistedSessionState,
                clr.upsert().ledger(),
                ranked,
                userInputRequired,
                depthOk,
                structuredParseFailed);
    }

    private void persistConfidenceBreakdown(
            String contextId,
            FeaturePlanState plan,
            Map<String, Object> spread,
            Map<String, Object> persistedSessionState,
            UnresolvedItemLedger ledger,
            ClarificationProjection ranked,
            boolean userInputRequired,
            boolean depthOk,
            boolean structuredParseFailed) {
        if (planStateStore == null || contextId == null || contextId.isBlank()) {
            return;
        }
        double clar = 0.0;
        try {
            String s = getString(spread, PLANNING_CLARIFICATION_CONFIDENCE_SCORE_KEY);
            if (s != null && !s.isBlank()) {
                clar = Double.parseDouble(s.trim());
            }
        } catch (NumberFormatException ignored) {
        }
        FeaturePlanState latest = planStateStore.getByContextId(contextId).orElse(plan);
        if (latest == null) {
            return;
        }
        String repoJson = getString(persistedSessionState, "planningRepoEvidenceJson");
        PlanningConfidenceBreakdown bd =
                PlanningConfidenceService.cycleBreakdown(
                        latest,
                        clar,
                        depthOk,
                        structuredParseFailed,
                        ledger,
                        ranked,
                        userInputRequired,
                        repoJson != null ? repoJson : "");
        planStateStore.update(latest.withPlanningConfidenceBreakdownJson(bd.toJson()));
    }

    private static String clarificationGapId(ClarificationProjection ranked) {
        if (ranked == null || ranked.metaJson() == null || ranked.metaJson().isBlank()) {
            return "";
        }
        try {
            Map<String, Object> meta = JSON.readValue(ranked.metaJson(), new TypeReference<>() {});
            Object gapId = meta.get("gapId");
            return gapId != null ? gapId.toString().trim() : "";
        } catch (Exception e) {
            return "";
        }
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
        return CanonicalPlanningGapEngine.semanticClarificationAllowed(plan, state, draftingCompletedThisInvocation);
    }

    private static void applyClarificationConfidence(
            Map<String, Object> spread,
            Map<String, Object> state,
            FeaturePlanState plan,
            UnresolvedItemLedger ledger,
            ClarificationProjection ranked,
            WorkProfileDefinition profile,
            boolean userInputRequired,
            boolean depthOk,
            boolean structuredParseFailed) {
        double confidence =
                clarificationConfidenceScore(plan, ledger, ranked, state, userInputRequired, depthOk, structuredParseFailed);
        double threshold =
                profile != null
                        ? profile.getCoordinatorClarification().getEnginePolicy().getClarificationConfidenceThreshold()
                        : com.vinekeepers.profile.CoordinatorClarificationEnginePolicy.defaultPolicy()
                                .getClarificationConfidenceThreshold();
        spread.put(PLANNING_CLARIFICATION_CONFIDENCE_SCORE_KEY, formatDouble(confidence));
        spread.put(PLANNING_CLARIFICATION_CONFIDENCE_HIGH_KEY, confidence >= threshold ? "true" : "false");
    }

    private static double clarificationConfidenceScore(
            FeaturePlanState plan,
            UnresolvedItemLedger ledger,
            ClarificationProjection ranked,
            Map<String, Object> state,
            boolean userInputRequired,
            boolean depthOk,
            boolean structuredParseFailed) {
        PlanConfidence stored = plan != null ? plan.getPlanConfidence() : null;
        if (stored != null && stored.getConfidenceScore() >= 0) {
            return clampScore(stored.getConfidenceScore());
        }
        double score = 0.18;
        if (depthOk) {
            score += 0.24;
        }
        if (!structuredParseFailed) {
            score += 0.08;
        }
        score += 0.28 * PlanningConfidenceService.repoEvidenceGroundingScore(plan, getString(state, "planningRepoEvidenceJson"));
        score += Math.min(0.22, clarificationKnownFactCount(plan) * 0.012);
        score -= Math.min(
                0.55,
                clarificationMaterialUnknownCount(plan, ledger, ranked, userInputRequired, structuredParseFailed) * 0.14);
        return clampScore(score);
    }

    private static int clarificationKnownFactCount(FeaturePlanState plan) {
        if (plan == null) {
            return 0;
        }
        int known = 0;
        known += plan.getRequirements().size();
        known += plan.getDecisions().size();
        known += plan.getValidationNotes().size();
        known += plan.getRisks().size();
        String featureSummary = PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "feature_summary");
        if (featureSummary != null && featureSummary.trim().length() >= 24) {
            known += 2;
        }
        String exploration = PlanningArtifactTexts.artifactField(plan, "request_exploration", "analysis", "exploration_body");
        if (exploration != null && exploration.trim().length() >= 120) {
            known += 2;
        }
        return known;
    }

    private static int clarificationMaterialUnknownCount(
            FeaturePlanState plan,
            UnresolvedItemLedger ledger,
            ClarificationProjection ranked,
            boolean userInputRequired,
            boolean structuredParseFailed) {
        int unknowns = 0;
        if (userInputRequired) {
            unknowns++;
        }
        if (structuredParseFailed) {
            unknowns++;
        }
        if (ranked != null && ranked.userInputRequired() && ranked.questionText() != null && !ranked.questionText().isBlank()) {
            unknowns++;
        }
        if (ledger != null) {
            for (UnresolvedItem it : ledger.items()) {
                if (it.getStatus() == UnresolvedItemStatus.OPEN
                        && "planning_clarification".equals(it.getSource().get("channel"))) {
                    unknowns++;
                }
            }
        }
        if (plan == null) {
            return unknowns;
        }
        for (String q : plan.getUnresolvedQuestions()) {
            if (q != null && !q.isBlank()) {
                unknowns++;
            }
        }
        for (PlanIssue issue : plan.getIssues()) {
            if (issue.isBlocking() && PlanIssueStatus.OPEN.equalsIgnoreCase(issue.getStatus())) {
                unknowns++;
            }
        }
        for (var assumption : plan.getAssumptions()) {
            if (PlanAssumptionStatus.OPEN.equalsIgnoreCase(assumption.getStatus())
                    && PlanGovernanceSeverity.HIGH.equalsIgnoreCase(assumption.getSeverity())) {
                unknowns++;
            }
        }
        return unknowns;
    }

    private static double clampScore(double score) {
        return Math.max(0.0, Math.min(1.0, score));
    }

    private static String formatDouble(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
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
            Map<String, Object> bind) {
        silentSynthesis.runExpansionPhase(event, work, spread, bind);
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
            ClarificationProjection ranked,
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
            ClarificationProjection ranked,
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
            ClarificationProjection ranked,
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
            ClarificationProjection ranked,
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
        return PlanningQuestionComposer.normalizeClarificationQuestion(raw);
    }

    private static Map<String, Object> baseSpread() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planningRoomCycleError", "");
        m.put("planningPhase", "");
        m.put("planningUserInputRequired", "false");
        m.put("planningCanonicalUserInputRequired", "false");
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
