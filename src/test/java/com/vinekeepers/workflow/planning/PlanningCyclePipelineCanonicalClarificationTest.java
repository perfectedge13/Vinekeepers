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
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.vinekeepers.workflow.planning.PlanningGapEvaluator.PLANNING_CLARIFICATION_CHANNEL;
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
        ClarificationProjection ranked =
                new ClarificationProjection(true, "", "[]", "{}", 1, List.of(), false, "Which API version?");
        String summary =
                PlanningCyclePipeline.buildOrchestratorSummary(plan, true, "", ranked, 2, false, false, "", true, false);
        assertFalse(summary.contains("Planning update"));
        assertFalse(summary.contains("Depth check passed"));
        assertFalse(summary.contains("planning packet"));
        assertFalse(summary.contains("Next I'll"));
    }

    @Test
    void orchestratorSummaryUsesResolvedGateNotRawLlmUserInputFlag() {
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
        ClarificationProjection rankedNoAsk =
                new ClarificationProjection(false, "", "[]", "{}", 0, List.of(), false, "");
        String waiting =
                PlanningCyclePipeline.buildOrchestratorSummary(
                        plan, true, "", rankedNoAsk, 1, false, false, "", true, false);
        assertTrue(waiting.contains("Paused until the detail below is answered"));
        assertFalse(waiting.contains("keep going"));

        ClarificationProjection rankedAsk =
                new ClarificationProjection(true, "", "[]", "{}", 1, List.of(), false, "Which version?");
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
    void canonicalProjectionDoesNotSurfaceTooShortOrWeakTemplate() {
        CoordinatorClarificationGapRule rule =
                new CoordinatorClarificationGapRule("g_low", false, "ok", List.of("x"), List.of(), List.of());
        CoordinatorClarificationSettings coord =
                new CoordinatorClarificationSettings(CoordinatorClarificationMode.CANONICAL_V1, List.of(rule));
        WorkProfileDefinition profile =
                new WorkProfileDefinition(
                        "p", "", List.of(), List.of(), false, false, List.of(), coord);
        CoordinatorClarificationGapEvaluator.OpenGap top = new CoordinatorClarificationGapEvaluator.OpenGap("g_low", false, "ok");
        ClarificationProjection projected =
                CanonicalClarificationSpreadBuilder.projectCanonicalGap(
                        UnresolvedItemLedger.empty(), profile, coord, top, "ok", List.of());
        assertFalse(projected.userInputRequired());
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
                PlanningQuestionComposer.composeQuestionForAskCycle("Original?", "g1", rule, ledger, 0);
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
                PlanningQuestionComposer.composeQuestionForAskCycle(
                        "Original?", "g1", rule, UnresolvedItemLedger.empty(), 2);
        assertTrue(q.contains("exactly one option"));
        assertTrue(q.contains("config only"));
    }

    @Test
    void canonicalProjectionSkipsStructuredChoicesWithoutGapBoundedUiOptIn() {
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
        ClarificationProjection ranked =
                CanonicalClarificationSpreadBuilder.projectCanonicalGap(
                        UnresolvedItemLedger.empty(),
                        profile,
                        coord,
                        top,
                        "Use option A or option B for timeouts?",
                        List.of());
        assertFalse(ranked.useStructuredChoices());
        assertTrue(ranked.userInputRequired());
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
