package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.CoordinatorClarificationGapRule;
import com.vinekeepers.profile.CoordinatorClarificationMode;
import com.vinekeepers.profile.CoordinatorClarificationSettings;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.profile.WorkProfileLoader;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.workflow.UnresolvedItem;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.state.workflow.UnresolvedItemStatus;
import com.vinekeepers.workflow.planning.PlanningQuestionRankingPolicy.RankedClarification;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.vinekeepers.workflow.planning.PlanningGapEvaluator.PLANNING_CLARIFICATION_CHANNEL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.state.planning.FeaturePlanState;

class PlanningCyclePipelineCanonicalClarificationTest {

    @Test
    void canonicalModeIgnoresStrayOpenLedgerWhenGapsClosed() {
        UnresolvedItem strayOpen =
                new UnresolvedItem(
                        "uq_stray",
                        "fp",
                        UnresolvedItemStatus.OPEN,
                        "",
                        "Stray open question",
                        "normal",
                        Map.of("channel", PLANNING_CLARIFICATION_CHANNEL, "gapId", "ghost"),
                        List.of(),
                        List.of(),
                        0);
        UnresolvedItemLedger ledger = UnresolvedItemLedger.empty().withAdded(strayOpen);
        assertFalse(
                PlanningCyclePipeline.resolvePlanningUserInputRequired(
                        true, false, ledger));
    }

    @Test
    void legacyModeStillUsesOpenLedger() {
        UnresolvedItem open =
                new UnresolvedItem(
                        "uq_1",
                        "fp",
                        UnresolvedItemStatus.OPEN,
                        "",
                        "Need answer",
                        "normal",
                        Map.of("channel", PLANNING_CLARIFICATION_CHANNEL),
                        List.of(),
                        List.of(),
                        0);
        UnresolvedItemLedger ledger = UnresolvedItemLedger.empty().withAdded(open);
        assertTrue(PlanningCyclePipeline.resolvePlanningUserInputRequired(false, false, ledger));
    }

    @Test
    void orchestratorSummaryAvoidsMachinePhrases() {
        var plan =
                new com.vinekeepers.state.planning.FeaturePlanState(
                        "c",
                        "f",
                        "s",
                        "room",
                        null,
                        null,
                        "t",
                        "Short request",
                        "PLANNING",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        com.vinekeepers.state.planning.FeaturePlanState.initialSectionStatuses(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "software_feature_planning",
                        Map.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        RankedClarification ranked =
                new RankedClarification(true, "", "[]", "{}", 1, List.of(), false, "Which API version?");
        String summary =
                PlanningCyclePipeline.buildOrchestratorSummary(plan, true, "", ranked, 2, false, false, "", true, false);
        assertFalse(summary.contains("Planning update"));
        assertFalse(summary.contains("Depth check passed"));
        assertFalse(summary.contains("planning packet"));
        assertFalse(summary.contains("Next I'll"));
    }

    @Test
    void orchestratorSummaryUsesResolvedGateNotRankedFlag() {
        FeaturePlanState plan =
                new FeaturePlanState(
                        "c",
                        "f",
                        "s",
                        "room",
                        null,
                        null,
                        "t",
                        "Short request",
                        "PLANNING",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        FeaturePlanState.initialSectionStatuses(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "software_feature_planning",
                        Map.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        RankedClarification rankedNoAsk =
                new RankedClarification(false, "", "[]", "{}", 0, List.of(), false, "");
        String waiting =
                PlanningCyclePipeline.buildOrchestratorSummary(
                        plan, true, "", rankedNoAsk, 1, false, false, "", true, false);
        assertTrue(waiting.contains("Paused until the detail below is answered"));
        assertFalse(waiting.contains("keep going"));

        RankedClarification rankedAsk =
                new RankedClarification(true, "", "[]", "{}", 1, List.of(), false, "Which version?");
        String moving =
                PlanningCyclePipeline.buildOrchestratorSummary(
                        plan, true, "", rankedAsk, 1, false, false, "", false, false);
        assertFalse(moving.contains("Paused until the detail below is answered"));
    }

    @Test
    void semanticClarificationAllowed_trueWhenSplitPathSpreadMarksFirstPass() {
        assertTrue(
                PlanningCyclePipeline.semanticClarificationAllowed(
                        null, Map.of("planningAutonomousFirstPassCompleted", "true"), false));
    }

    @Test
    void semanticClarificationAllowed_trueWhenDraftingCompletesThisInvocation() {
        assertTrue(PlanningCyclePipeline.semanticClarificationAllowed(null, Map.of(), true));
    }

    @Test
    void semanticClarificationAllowed_trueWhenPlanRecordsCompletedAutonomousPass() {
        FeaturePlanState plan = bareFeaturePlan().withAutonomousPlanningPassCompleted(true);
        assertTrue(PlanningCyclePipeline.semanticClarificationAllowed(plan, Map.of(), false));
    }

    @Test
    void semanticClarificationAllowed_falseWithoutPassSignals() {
        FeaturePlanState plan = bareFeaturePlanWithRequest("Lets plug different models into different workflow steps.");
        assertFalse(PlanningCyclePipeline.semanticClarificationAllowed(plan, Map.of(), false));
    }

    @Test
    void ensureRankedRepairsWhenRankerDropsShortCanonicalTemplate() {
        CoordinatorClarificationGapRule rule =
                new CoordinatorClarificationGapRule("g_low", false, "ok", List.of("x"), List.of(), List.of());
        CoordinatorClarificationSettings coord =
                new CoordinatorClarificationSettings(CoordinatorClarificationMode.CANONICAL_V1, List.of(rule));
        WorkProfileDefinition profile =
                new WorkProfileDefinition(
                        "p", "", List.of(), List.of(), false, false, List.of(), coord);
        CoordinatorClarificationGapEvaluator.OpenGap top = new CoordinatorClarificationGapEvaluator.OpenGap("g_low", false, "ok");
        RankedClarification dropped =
                com.vinekeepers.workflow.planning.PlanningQuestionRankingPolicy.rank(
                        null, List.of("ok"), 3, UnresolvedItemLedger.empty(), false, false);
        assertFalse(dropped.userInputRequired());
        RankedClarification repaired =
                invokeEnsureRankedForOpenCanonicalGap(
                        null, UnresolvedItemLedger.empty(), profile, coord, top, dropped, 0);
        assertTrue(repaired.userInputRequired());
        assertFalse(repaired.questionText() == null || repaired.questionText().isBlank());
    }

    @Test
    void canonicalQuestionEscalatesWhenSameAsLastMerged() {
        CoordinatorClarificationGapRule rule =
                new CoordinatorClarificationGapRule(
                        "g1",
                        false,
                        "Original?",
                        List.of(),
                        List.of(),
                        List.of(),
                        false,
                        false,
                        "Please add one concrete constraint.");
        UnresolvedItem merged =
                new UnresolvedItem(
                        "m1",
                        "fp",
                        UnresolvedItemStatus.MERGED,
                        "",
                        "Original?",
                        "normal",
                        Map.of("channel", PLANNING_CLARIFICATION_CHANNEL, "gapId", "g1"),
                        List.of(),
                        List.of(),
                        0);
        UnresolvedItemLedger ledger = UnresolvedItemLedger.empty().withAdded(merged);
        String q =
                PlanningCyclePipeline.effectiveCanonicalQuestionText("Original?", "g1", rule, ledger, 0);
        assertTrue(q.contains("concrete constraint"));
    }

    @Test
    void canonicalQuestionEscalatesToBoundedReplyAfterMultipleAsks() {
        CoordinatorClarificationGapRule rule =
                new CoordinatorClarificationGapRule(
                        "g1",
                        false,
                        "Original?",
                        List.of(),
                        List.of(),
                        List.of("config only", "runtime only", "both"));
        String q =
                PlanningCyclePipeline.effectiveCanonicalQuestionText(
                        "Original?", "g1", rule, UnresolvedItemLedger.empty(), 2);
        assertTrue(q.contains("exactly one option"));
        assertTrue(q.contains("config only"));
    }

    @Test
    void canonicalRankSkipsStructuredChoicesWithoutGapOptIn() {
        CoordinatorClarificationSettings coord =
                new CoordinatorClarificationSettings(
                        CoordinatorClarificationMode.CANONICAL_V1,
                        List.of(
                                new CoordinatorClarificationGapRule(
                                        "g_or",
                                        false,
                                        "Use option A or option B for timeouts?",
                                        List.of("timeout"),
                                        List.of(),
                                        List.of())));
        var top =
                new CoordinatorClarificationGapEvaluator.OpenGap("g_or", false, "Use option A or option B for timeouts?");
        WorkProfileDefinition profile =
                new WorkProfileDefinition(
                        "p",
                        "",
                        List.of(),
                        List.of(),
                        true,
                        true,
                        List.of(),
                        coord);
        RankedClarification ranked =
                invokeRankCanonicalOpenTopGap(null, UnresolvedItemLedger.empty(), profile, coord, top, 0);
        assertFalse(ranked.useStructuredChoices());
    }

    @Test
    void applyAskOneQuestionUserVisibleCopy_forcesQuestionOnlyOrchestratorAndProgressLines() {
        FeaturePlanState plan = bareFeaturePlan();
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        store.update(plan);
        WorkProfileRegistry registry = new WorkProfileRegistry();
        PlanningCyclePipeline pipeline = new PlanningCyclePipeline(null, store, registry);
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put(PlanningCanonicalDecisionSupport.CANONICAL_NEXT_ACTION_KEY, "ASK_ONE_QUESTION");
        spread.put("planningClarificationQuestionText", "Which concrete package should own the new type?");
        spread.put("planningOrchestratorRoundSummary", "**What I'm tracking:** should be replaced");
        spread.put("planningCycleProgressSummary", "old progress");
        spread.put("userCopyCoordinatorProgress", "old user copy");
        invokeApplyAskOneQuestionUserVisibleCopy(pipeline, "c", spread, plan);
        String expected = "❓ Which concrete package should own the new type?";
        assertEquals(expected, spread.get("planningOrchestratorRoundSummary"));
        assertEquals(expected, spread.get("planningCycleProgressSummary"));
        assertEquals(expected, spread.get("userCopyCoordinatorProgress"));
    }

    @Test
    void applyAskOneQuestionUserVisibleCopy_prefixesStuckHintBeforeQuestion() {
        FeaturePlanState plan = bareFeaturePlan();
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        store.update(plan);
        PlanningCyclePipeline pipeline = new PlanningCyclePipeline(null, store, new WorkProfileRegistry());
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put(PlanningCanonicalDecisionSupport.CANONICAL_NEXT_ACTION_KEY, "ASK_ONE_QUESTION");
        spread.put("planningClarificationQuestionText", "Which API version?");
        spread.put("planningClarificationStuck", "true");
        spread.put("planningClarificationStuckHint", "This clarification thread has been waiting.");
        invokeApplyAskOneQuestionUserVisibleCopy(pipeline, "c", spread, plan);
        assertEquals(
                "This clarification thread has been waiting.\n\n❓ Which API version?",
                spread.get("planningOrchestratorRoundSummary"));
    }

    @Test
    void applyAskOneQuestionUserVisibleCopy_noOpWhenGovernorActionIsNotAskOneQuestion() {
        FeaturePlanState plan = bareFeaturePlan();
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        store.update(plan);
        PlanningCyclePipeline pipeline = new PlanningCyclePipeline(null, store, new WorkProfileRegistry());
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put(PlanningCanonicalDecisionSupport.CANONICAL_NEXT_ACTION_KEY, "POST_PACKET");
        spread.put("planningOrchestratorRoundSummary", "keep me");
        invokeApplyAskOneQuestionUserVisibleCopy(pipeline, "c", spread, plan);
        assertEquals("keep me", spread.get("planningOrchestratorRoundSummary"));
    }

    @Test
    void applyAskOneQuestionUserVisibleCopy_usesFallbackWhenQuestionTextMissing() {
        FeaturePlanState plan = bareFeaturePlan();
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        store.update(plan);
        PlanningCyclePipeline pipeline = new PlanningCyclePipeline(null, store, new WorkProfileRegistry());
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put(PlanningCanonicalDecisionSupport.CANONICAL_NEXT_ACTION_KEY, "ASK_ONE_QUESTION");
        spread.put("planningClarificationQuestionText", "");
        invokeApplyAskOneQuestionUserVisibleCopy(pipeline, "c", spread, plan);
        String summary = String.valueOf(spread.get("planningOrchestratorRoundSummary"));
        assertTrue(summary.startsWith("❓ "));
        assertTrue(summary.contains("single most important constraint"));
    }

    @Test
    void splitExpansionSkipsFullRescanAfterMergedClarification() {
        WorkProfileRegistry registry = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        FeaturePlanState plan = bareFeaturePlanV2();
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        store.update(plan);
        PlanningCyclePipeline pipeline = new PlanningCyclePipeline(null, store, registry);
        Map<String, Object> spread =
                pipeline.runExpansionPhaseOnly(
                        null,
                        Map.of(
                                "contextId", "c",
                                "planningJustMergedClarification", "true"),
                        Map.of());
        assertTrue("true".equals(spread.get("planningSelectiveRerunActive")));
        assertTrue(String.valueOf(spread.get("planningSelectiveRerunNote")).contains("skipping a full re-scan"));
        assertFalse(spread.containsKey("planningPartialAggregatedFollowUpsJson"));
    }

    private static void invokeApplyAskOneQuestionUserVisibleCopy(
            PlanningCyclePipeline pipeline,
            String contextId,
            Map<String, Object> spread,
            FeaturePlanState plan) {
        try {
            var m =
                    PlanningCyclePipeline.class.getDeclaredMethod(
                            "applyAskOneQuestionUserVisibleCopy",
                            String.class,
                            Map.class,
                            FeaturePlanState.class);
            m.setAccessible(true);
            m.invoke(pipeline, contextId, spread, plan);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static RankedClarification invokeEnsureRankedForOpenCanonicalGap(
            FeaturePlanState plan,
            UnresolvedItemLedger ledger,
            WorkProfileDefinition profile,
            CoordinatorClarificationSettings coord,
            CoordinatorClarificationGapEvaluator.OpenGap top,
            RankedClarification ranked,
            int priorAskCount) {
        try {
            var m =
                    PlanningCyclePipeline.class.getDeclaredMethod(
                            "ensureRankedForOpenCanonicalGap",
                            FeaturePlanState.class,
                            UnresolvedItemLedger.class,
                            WorkProfileDefinition.class,
                            CoordinatorClarificationSettings.class,
                            CoordinatorClarificationGapEvaluator.OpenGap.class,
                            RankedClarification.class,
                            int.class);
            m.setAccessible(true);
            return (RankedClarification) m.invoke(null, plan, ledger, profile, coord, top, ranked, priorAskCount);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static RankedClarification invokeRankCanonicalOpenTopGap(
            FeaturePlanState plan,
            UnresolvedItemLedger ledger,
            WorkProfileDefinition profile,
            CoordinatorClarificationSettings coord,
            CoordinatorClarificationGapEvaluator.OpenGap top,
            int priorAskCount) {
        try {
            var m =
                    PlanningCyclePipeline.class.getDeclaredMethod(
                            "rankCanonicalOpenTopGap",
                            com.vinekeepers.state.planning.FeaturePlanState.class,
                            UnresolvedItemLedger.class,
                            WorkProfileDefinition.class,
                            CoordinatorClarificationSettings.class,
                            CoordinatorClarificationGapEvaluator.OpenGap.class,
                            int.class);
            m.setAccessible(true);
            return (RankedClarification) m.invoke(null, plan, ledger, profile, coord, top, priorAskCount);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static FeaturePlanState bareFeaturePlan() {
        return bareFeaturePlanWithRequest("Short request");
    }

    private static FeaturePlanState bareFeaturePlanWithRequest(String request) {
        return new FeaturePlanState(
                "c",
                "f",
                "s",
                "room",
                null,
                null,
                "t",
                request,
                "PLANNING",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                FeaturePlanState.initialSectionStatuses(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "software_feature_planning",
                Map.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static FeaturePlanState bareFeaturePlanV2() {
        return new FeaturePlanState(
                "c",
                "f",
                "s",
                "room",
                null,
                null,
                "t",
                "Short request",
                "PLANNING",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                FeaturePlanState.initialSectionStatuses(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "software_feature_planning_v2",
                Map.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
