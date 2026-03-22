package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.CoordinatorClarificationGapRule;
import com.vinekeepers.profile.CoordinatorClarificationMode;
import com.vinekeepers.profile.CoordinatorClarificationSettings;
import com.vinekeepers.state.workflow.UnresolvedItem;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.state.workflow.UnresolvedItemStatus;
import com.vinekeepers.workflow.planning.PlanningQuestionRankingPolicy.RankedClarification;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.vinekeepers.workflow.planning.PlanningGapEvaluator.PLANNING_CLARIFICATION_CHANNEL;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                        null);
        RankedClarification ranked =
                new RankedClarification(true, "", "[]", "{}", 1, List.of(), false, "Which API version?");
        String summary =
                PlanningCyclePipeline.buildOrchestratorSummary(plan, true, "", ranked, 2, false, false, "");
        assertFalse(summary.contains("Planning update"));
        assertFalse(summary.contains("Depth check passed"));
        assertFalse(summary.contains("planning packet"));
        assertFalse(summary.contains("Next I'll"));
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
                PlanningCyclePipeline.effectiveCanonicalQuestionText("Original?", "g1", rule, ledger);
        assertTrue(q.contains("concrete constraint"));
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
        com.vinekeepers.profile.WorkProfileDefinition profile =
                new com.vinekeepers.profile.WorkProfileDefinition(
                        "p",
                        "",
                        List.of(),
                        List.of(),
                        true,
                        true,
                        List.of(),
                        coord);
        RankedClarification ranked =
                invokeRankCanonicalOpenTopGap(null, UnresolvedItemLedger.empty(), profile, coord, top);
        assertFalse(ranked.useStructuredChoices());
    }

    private static RankedClarification invokeRankCanonicalOpenTopGap(
            com.vinekeepers.state.planning.FeaturePlanState plan,
            UnresolvedItemLedger ledger,
            com.vinekeepers.profile.WorkProfileDefinition profile,
            CoordinatorClarificationSettings coord,
            CoordinatorClarificationGapEvaluator.OpenGap top) {
        try {
            var m =
                    PlanningCyclePipeline.class.getDeclaredMethod(
                            "rankCanonicalOpenTopGap",
                            com.vinekeepers.state.planning.FeaturePlanState.class,
                            UnresolvedItemLedger.class,
                            com.vinekeepers.profile.WorkProfileDefinition.class,
                            CoordinatorClarificationSettings.class,
                            CoordinatorClarificationGapEvaluator.OpenGap.class);
            m.setAccessible(true);
            return (RankedClarification) m.invoke(null, plan, ledger, profile, coord, top);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
