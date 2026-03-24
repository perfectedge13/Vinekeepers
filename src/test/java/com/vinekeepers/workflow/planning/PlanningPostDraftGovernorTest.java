package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanningFailureCategory;
import com.vinekeepers.state.workflow.UnresolvedItem;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.state.workflow.UnresolvedItemStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.vinekeepers.workflow.planning.PlanningGapEvaluator.PLANNING_CLARIFICATION_CHANNEL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningPostDraftGovernorTest {

    @Test
    void derive_postPacketWhenReady() {
        Map<String, Object> persisted = Map.of();
        Map<String, Object> signal = Map.of("planningRepoEvidenceJson", "{}");
        PlanningPostDraftGovernor.Result r =
                PlanningPostDraftGovernor.derive(
                        persisted,
                        signal,
                        minimalPlan(),
                        UnresolvedItemLedger.empty(),
                        false,
                        true,
                        true,
                        false,
                        "",
                        "",
                        "",
                        false,
                        false,
                        false);
        assertEquals(PlanningPostDraftAction.POST_PACKET, r.action());
    }

    @Test
    void derive_askWhenUserInputRequired() {
        PlanningPostDraftGovernor.Result r =
                PlanningPostDraftGovernor.derive(
                        Map.of(),
                        Map.of(),
                        minimalPlan(),
                        UnresolvedItemLedger.empty(),
                        true,
                        false,
                        true,
                        false,
                        "",
                        "",
                        "",
                        false,
                        false,
                        false);
        assertEquals(PlanningPostDraftAction.ASK_ONE_QUESTION, r.action());
    }

    @Test
    void derive_autonomousRedraftWhenRevisionNeededAndMaterial() {
        Map<String, Object> persisted = Map.of();
        Map<String, Object> signal = Map.of("planningRepoEvidenceJson", "{\"x\":1}");
        PlanningPostDraftGovernor.Result r =
                PlanningPostDraftGovernor.derive(
                        persisted,
                        signal,
                        minimalPlan(),
                        UnresolvedItemLedger.empty(),
                        false,
                        false,
                        false,
                        false,
                        "thin",
                        "",
                        "",
                        false,
                        false,
                        false);
        assertEquals(PlanningPostDraftAction.AUTONOMOUS_REDRAFT, r.action());
    }

    @Test
    void derive_blocksWhenHardClarificationBlock() {
        PlanningPostDraftGovernor.Result r =
                PlanningPostDraftGovernor.derive(
                        Map.of(),
                        Map.of(),
                        minimalPlan(),
                        UnresolvedItemLedger.empty(),
                        false,
                        false,
                        true,
                        false,
                        "",
                        "",
                        "",
                        false,
                        false,
                        true);
        assertEquals(PlanningPostDraftAction.BLOCK, r.action());
    }

    @Test
    void derive_hardClarificationBlockWithOpenQuestion_asksInsteadOfBlocking() {
        UnresolvedItem it =
                new UnresolvedItem(
                        "uq_1",
                        "fp",
                        UnresolvedItemStatus.OPEN,
                        "",
                        "Which workflow steps need overrides first?",
                        "blocking",
                        Map.of("channel", PLANNING_CLARIFICATION_CHANNEL, "gapId", "g1"),
                        List.of(),
                        List.of(),
                        0);
        UnresolvedItemLedger ledger = UnresolvedItemLedger.empty().withAdded(it);
        PlanningPostDraftGovernor.Result r =
                PlanningPostDraftGovernor.derive(
                        Map.of(),
                        Map.of(),
                        minimalPlan(),
                        ledger,
                        false,
                        false,
                        true,
                        false,
                        "",
                        "",
                        "",
                        false,
                        false,
                        true);
        assertEquals(PlanningPostDraftAction.ASK_ONE_QUESTION, r.action());
        assertTrue(r.forceUserInputRequired());
    }

    @Test
    void derive_hardClarificationBlockAfterSuccessfulMerge_continues() {
        PlanningPostDraftGovernor.Result r =
                PlanningPostDraftGovernor.derive(
                        Map.of(),
                        Map.of("planningJustMergedClarification", "true"),
                        minimalPlan(),
                        UnresolvedItemLedger.empty(),
                        false,
                        false,
                        true,
                        false,
                        "",
                        "",
                        "",
                        false,
                        false,
                        true);
        assertEquals(PlanningPostDraftAction.ASSUME_AND_CONTINUE, r.action());
        assertTrue(r.noticeMarkdown().contains("clarification was applied successfully"));
    }

    @Test
    void derive_asksWhenLlmSuggestedQuestionExistsEvenWithoutCanonicalPending() {
        UnresolvedItem it =
                new UnresolvedItem(
                        "uq_1",
                        "fp",
                        UnresolvedItemStatus.OPEN,
                        "",
                        "Which workflow steps should support model overrides first?",
                        "normal",
                        Map.of("channel", PLANNING_CLARIFICATION_CHANNEL),
                        List.of(),
                        List.of(),
                        0);
        UnresolvedItemLedger ledger = UnresolvedItemLedger.empty().withAdded(it);
        PlanningPostDraftGovernor.Result r =
                PlanningPostDraftGovernor.derive(
                        Map.of(),
                        Map.of("planningLlmUserInputSuggested", "true"),
                        minimalPlan(),
                        ledger,
                        false,
                        false,
                        true,
                        false,
                        "",
                        "",
                        "",
                        false,
                        false,
                        false);
        assertEquals(PlanningPostDraftAction.ASK_ONE_QUESTION, r.action());
        assertTrue(r.forceUserInputRequired());
    }

    @Test
    void derive_forcesAskWhenRepeatLedgerAndNoMaterial() {
        UnresolvedItem it =
                new UnresolvedItem(
                        "uq_1",
                        "fp",
                        UnresolvedItemStatus.OPEN,
                        "",
                        "Which OAuth flow?",
                        "normal",
                        Map.of("channel", PLANNING_CLARIFICATION_CHANNEL, "gapId", "g1"),
                        List.of(),
                        List.of(),
                        1);
        UnresolvedItemLedger ledger = UnresolvedItemLedger.empty().withAdded(it);
        Map<String, Object> persisted = new LinkedHashMap<>();
        persisted.put(PlanningPostDraftGovernor.BASELINE_REPO_HASH_KEY, String.valueOf("{}".hashCode()));
        persisted.put(PlanningPostDraftGovernor.BASELINE_ASSUMPTION_COUNT_KEY, "0");
        persisted.put(PlanningPostDraftGovernor.BASELINE_CRITIQUE_BLOCKING_KEY, "0");
        Map<String, Object> signal = new LinkedHashMap<>();
        signal.put("planningRepoEvidenceJson", "{}");
        String situation =
                PlanningPostDraftGovernor.revisionSituationFingerprint(
                        false, false, "thin", false, false, ledger);
        persisted.put(PlanningPostDraftGovernor.LAST_REVISION_SITUATION_KEY, situation);

        PlanningPostDraftGovernor.Result r =
                PlanningPostDraftGovernor.derive(
                        persisted,
                        signal,
                        minimalPlan(),
                        ledger,
                        false,
                        false,
                        false,
                        false,
                        "thin",
                        "",
                        "",
                        false,
                        false,
                        false);
        assertEquals(PlanningPostDraftAction.ASK_ONE_QUESTION, r.action());
        assertTrue(r.forceUserInputRequired());
    }

    @Test
    void derive_forcesAskWhenSameNonPostingSituationEvenWithoutRepeatCount() {
        UnresolvedItem it =
                new UnresolvedItem(
                        "uq_1",
                        "fp",
                        UnresolvedItemStatus.OPEN,
                        "",
                        "Which OAuth flow?",
                        "normal",
                        Map.of("channel", PLANNING_CLARIFICATION_CHANNEL, "gapId", "g1"),
                        List.of(),
                        List.of(),
                        0);
        UnresolvedItemLedger ledger = UnresolvedItemLedger.empty().withAdded(it);
        Map<String, Object> persisted = new LinkedHashMap<>();
        persisted.put(PlanningPostDraftGovernor.BASELINE_REPO_HASH_KEY, String.valueOf("{}".hashCode()));
        persisted.put(PlanningPostDraftGovernor.BASELINE_ASSUMPTION_COUNT_KEY, "0");
        persisted.put(PlanningPostDraftGovernor.BASELINE_CRITIQUE_BLOCKING_KEY, "0");
        Map<String, Object> signal = new LinkedHashMap<>();
        signal.put("planningRepoEvidenceJson", "{}");
        String situation =
                PlanningPostDraftGovernor.revisionSituationFingerprint(
                        false, false, "thin", false, false, ledger);
        persisted.put(PlanningPostDraftGovernor.LAST_REVISION_SITUATION_KEY, situation);

        PlanningPostDraftGovernor.Result r =
                PlanningPostDraftGovernor.derive(
                        persisted,
                        signal,
                        minimalPlan(),
                        ledger,
                        false,
                        false,
                        false,
                        false,
                        "thin",
                        "",
                        "",
                        false,
                        false,
                        false);
        assertEquals(PlanningPostDraftAction.ASK_ONE_QUESTION, r.action());
        assertTrue(r.forceUserInputRequired());
    }

    @Test
    void derive_forcesAskWhenStalledWithStructuredPlanGapsAndNoLedgerQuestion() {
        UnresolvedItem it =
                new UnresolvedItem(
                        "uq_1",
                        "fp",
                        UnresolvedItemStatus.OPEN,
                        "",
                        "",
                        "normal",
                        Map.of("channel", PLANNING_CLARIFICATION_CHANNEL, "gapId", "g1"),
                        List.of(),
                        List.of(),
                        1);
        UnresolvedItemLedger ledger = UnresolvedItemLedger.empty().withAdded(it);
        Map<String, Object> persisted = new LinkedHashMap<>();
        persisted.put(PlanningPostDraftGovernor.BASELINE_REPO_HASH_KEY, String.valueOf("{}".hashCode()));
        persisted.put(PlanningPostDraftGovernor.BASELINE_ASSUMPTION_COUNT_KEY, "0");
        persisted.put(PlanningPostDraftGovernor.BASELINE_CRITIQUE_BLOCKING_KEY, "0");
        Map<String, Object> signal = new LinkedHashMap<>();
        signal.put("planningRepoEvidenceJson", "{}");
        String situation =
                PlanningPostDraftGovernor.revisionSituationFingerprint(
                        false, false, "thin", false, false, ledger);
        persisted.put(PlanningPostDraftGovernor.LAST_REVISION_SITUATION_KEY, situation);

        FeaturePlanState plan =
                minimalPlan()
                        .withGovernanceRecords(
                                List.of(), List.of(), List.of(), List.of(), List.of("What latency SLO applies?"));

        PlanningPostDraftGovernor.Result r =
                PlanningPostDraftGovernor.derive(
                        persisted,
                        signal,
                        plan,
                        ledger,
                        false,
                        false,
                        false,
                        false,
                        "thin",
                        "",
                        "",
                        false,
                        false,
                        false);
        assertEquals(PlanningPostDraftAction.ASK_ONE_QUESTION, r.action());
        assertTrue(r.forceUserInputRequired());
    }

    @Test
    void derive_assumeWhenStalledWithoutLedgerQuestion() {
        UnresolvedItem it =
                new UnresolvedItem(
                        "uq_1",
                        "fp",
                        UnresolvedItemStatus.OPEN,
                        "",
                        "",
                        "normal",
                        Map.of("channel", PLANNING_CLARIFICATION_CHANNEL, "gapId", "g1"),
                        List.of(),
                        List.of(),
                        1);
        UnresolvedItemLedger ledger = UnresolvedItemLedger.empty().withAdded(it);
        Map<String, Object> persisted = new LinkedHashMap<>();
        persisted.put(PlanningPostDraftGovernor.BASELINE_REPO_HASH_KEY, String.valueOf("{}".hashCode()));
        persisted.put(PlanningPostDraftGovernor.BASELINE_ASSUMPTION_COUNT_KEY, "0");
        persisted.put(PlanningPostDraftGovernor.BASELINE_CRITIQUE_BLOCKING_KEY, "0");
        Map<String, Object> signal = new LinkedHashMap<>();
        signal.put("planningRepoEvidenceJson", "{}");
        String situation =
                PlanningPostDraftGovernor.revisionSituationFingerprint(
                        false, false, "thin", false, false, ledger);
        persisted.put(PlanningPostDraftGovernor.LAST_REVISION_SITUATION_KEY, situation);

        PlanningPostDraftGovernor.Result r =
                PlanningPostDraftGovernor.derive(
                        persisted,
                        signal,
                        minimalPlan(),
                        ledger,
                        false,
                        false,
                        false,
                        false,
                        "thin",
                        "",
                        "",
                        false,
                        false,
                        false);
        assertEquals(PlanningPostDraftAction.ASSUME_AND_CONTINUE, r.action());
    }

    @Test
    void derive_recoverableSynthesisFailure_withOpenClarification_asksOne() {
        UnresolvedItem it =
                new UnresolvedItem(
                        "uq_1",
                        "fp",
                        UnresolvedItemStatus.OPEN,
                        "",
                        "Pick A or B?",
                        "normal",
                        Map.of("channel", PLANNING_CLARIFICATION_CHANNEL, "gapId", "g1"),
                        List.of(),
                        List.of(),
                        0);
        UnresolvedItemLedger ledger = UnresolvedItemLedger.empty().withAdded(it);
        PlanningPostDraftGovernor.Result r =
                PlanningPostDraftGovernor.derive(
                        Map.of(),
                        Map.of(),
                        minimalPlan(),
                        ledger,
                        false,
                        false,
                        true,
                        false,
                        "",
                        "",
                        PlanningFailureCategory.SYNTHESIS_REPAIR_EXHAUSTED.name(),
                        true,
                        false,
                        false);
        assertEquals(PlanningPostDraftAction.ASK_ONE_QUESTION, r.action());
        assertTrue(r.forceUserInputRequired());
    }

    @Test
    void derive_recoverableSynthesisFailure_withStructuredGaps_asksOneQuestion() {
        FeaturePlanState plan =
                minimalPlan()
                        .withGovernanceRecords(
                                List.of(), List.of(), List.of(), List.of(), List.of("Confirm data retention?"));
        PlanningPostDraftGovernor.Result r =
                PlanningPostDraftGovernor.derive(
                        Map.of(),
                        Map.of(),
                        plan,
                        UnresolvedItemLedger.empty(),
                        false,
                        false,
                        true,
                        false,
                        "",
                        "",
                        PlanningFailureCategory.SYNTHESIS_JSON_INVALID.name(),
                        true,
                        false,
                        false);
        assertEquals(PlanningPostDraftAction.ASK_ONE_QUESTION, r.action());
        assertTrue(r.forceUserInputRequired());
    }

    @Test
    void derive_recoverableSynthesisFailure_noLedgerOrStructuredGaps_continuesWithSavedDraft() {
        PlanningPostDraftGovernor.Result r =
                PlanningPostDraftGovernor.derive(
                        Map.of(),
                        Map.of(),
                        minimalPlan(),
                        UnresolvedItemLedger.empty(),
                        false,
                        false,
                        true,
                        false,
                        "",
                        "",
                        PlanningFailureCategory.SYNTHESIS_UPSERT_REJECTED.name(),
                        true,
                        false,
                        false);
        assertEquals(PlanningPostDraftAction.ASSUME_AND_CONTINUE, r.action());
        assertFalse(r.forceUserInputRequired());
        assertTrue(r.noticeMarkdown().contains("current draft"));
    }

    @Test
    void derive_recoverableTransportFailure_continuesWithSavedDraft() {
        PlanningPostDraftGovernor.Result r =
                PlanningPostDraftGovernor.derive(
                        Map.of(),
                        Map.of(),
                        minimalPlan(),
                        UnresolvedItemLedger.empty(),
                        false,
                        false,
                        true,
                        false,
                        "",
                        "",
                        PlanningFailureCategory.SYNTHESIS_TRANSPORT_ERROR.name(),
                        true,
                        false,
                        false);
        assertEquals(PlanningPostDraftAction.ASSUME_AND_CONTINUE, r.action());
        assertTrue(r.noticeMarkdown().contains("current draft"));
    }

    @Test
    void derive_emptyNoopRecoverable_continuesWithoutBlocking() {
        PlanningPostDraftGovernor.Result r =
                PlanningPostDraftGovernor.derive(
                        Map.of(),
                        Map.of(),
                        minimalPlan(),
                        UnresolvedItemLedger.empty(),
                        false,
                        false,
                        true,
                        false,
                        "",
                        "",
                        PlanningFailureCategory.SYNTHESIS_EMPTY_NOOP.name(),
                        true,
                        false,
                        false);
        assertEquals(PlanningPostDraftAction.ASSUME_AND_CONTINUE, r.action());
    }

    @Test
    void derive_synthesisFailure_notRecoverable_blocks() {
        PlanningPostDraftGovernor.Result r =
                PlanningPostDraftGovernor.derive(
                        Map.of(),
                        Map.of(),
                        minimalPlan(),
                        UnresolvedItemLedger.empty(),
                        false,
                        false,
                        true,
                        false,
                        "",
                        "",
                        PlanningFailureCategory.SYNTHESIS_REPAIR_EXHAUSTED.name(),
                        false,
                        false,
                        false);
        assertEquals(PlanningPostDraftAction.BLOCK, r.action());
        assertTrue(r.noticeMarkdown().contains("Planning paused"));
    }

    @Test
    void derive_wantsRevision_suppressAutonomousRedraftNotice_assumesContinue() {
        Map<String, Object> persisted = Map.of();
        Map<String, Object> signal = Map.of("planningRepoEvidenceJson", "{\"x\":1}");
        PlanningPostDraftGovernor.Result r =
                PlanningPostDraftGovernor.derive(
                        persisted,
                        signal,
                        minimalPlan(),
                        UnresolvedItemLedger.empty(),
                        false,
                        false,
                        false,
                        false,
                        "thin",
                        "",
                        "",
                        false,
                        true,
                        false);
        assertEquals(PlanningPostDraftAction.ASSUME_AND_CONTINUE, r.action());
        assertTrue(r.noticeMarkdown().contains("without a separate redraft notice"));
    }

    @Test
    void derive_thresholdReachedWithoutFreshQuestion_assumesContinue() {
        Map<String, Object> persisted = new LinkedHashMap<>();
        persisted.put(PlanningPostDraftGovernor.AUTONOMOUS_REDRAFT_COUNT_KEY, "2");
        Map<String, Object> signal = new LinkedHashMap<>();
        signal.put("planningRepoEvidenceJson", "{}");
        signal.put(PlanningCyclePipeline.PLANNING_CLARIFICATION_CONFIDENCE_SCORE_KEY, "0.41");
        PlanningPostDraftGovernor.Result r =
                PlanningPostDraftGovernor.derive(
                        persisted,
                        signal,
                        minimalPlan(),
                        UnresolvedItemLedger.empty(),
                        false,
                        false,
                        false,
                        false,
                        "thin",
                        "",
                        "",
                        false,
                        false,
                        false);
        assertEquals(PlanningPostDraftAction.ASSUME_AND_CONTINUE, r.action());
        assertTrue(r.noticeMarkdown().contains("loop cap"));
    }

    @Test
    void derive_thresholdReachedWithFreshQuestion_asksOneQuestion() {
        UnresolvedItem it =
                new UnresolvedItem(
                        "uq_1",
                        "fp",
                        UnresolvedItemStatus.OPEN,
                        "",
                        "Which workflow step still needs a different model?",
                        "normal",
                        Map.of("channel", PLANNING_CLARIFICATION_CHANNEL, "gapId", "g1"),
                        List.of(),
                        List.of(),
                        0);
        Map<String, Object> persisted = new LinkedHashMap<>();
        persisted.put(PlanningPostDraftGovernor.AUTONOMOUS_REDRAFT_COUNT_KEY, "2");
        Map<String, Object> signal = new LinkedHashMap<>();
        signal.put("planningRepoEvidenceJson", "{}");
        signal.put(PlanningCyclePipeline.PLANNING_CLARIFICATION_CONFIDENCE_SCORE_KEY, "0.38");
        PlanningPostDraftGovernor.Result r =
                PlanningPostDraftGovernor.derive(
                        persisted,
                        signal,
                        minimalPlan(),
                        UnresolvedItemLedger.empty().withAdded(it),
                        false,
                        false,
                        false,
                        false,
                        "thin",
                        "",
                        "",
                        false,
                        false,
                        false);
        assertEquals(PlanningPostDraftAction.ASK_ONE_QUESTION, r.action());
        assertTrue(r.forceUserInputRequired());
    }

    @Test
    void derive_highConfidenceBreaksLoopBeforeAnotherRedraft() {
        Map<String, Object> signal = new LinkedHashMap<>();
        signal.put("planningRepoEvidenceJson", "{}");
        signal.put(PlanningCyclePipeline.PLANNING_CLARIFICATION_CONFIDENCE_SCORE_KEY, "0.86");
        PlanningPostDraftGovernor.Result r =
                PlanningPostDraftGovernor.derive(
                        Map.of(),
                        signal,
                        minimalPlan(),
                        UnresolvedItemLedger.empty(),
                        false,
                        false,
                        false,
                        false,
                        "thin",
                        "",
                        "",
                        false,
                        false,
                        false);
        assertEquals(PlanningPostDraftAction.ASSUME_AND_CONTINUE, r.action());
        assertTrue(r.noticeMarkdown().contains("confidence threshold"));
    }

    @Test
    void revisionSituationFingerprint_stableForSameInputs() {
        UnresolvedItemLedger ledger = UnresolvedItemLedger.empty();
        String a =
                PlanningPostDraftGovernor.revisionSituationFingerprint(
                        false, true, "parse", false, false, ledger);
        String b =
                PlanningPostDraftGovernor.revisionSituationFingerprint(
                        false, true, "parse", false, false, ledger);
        assertEquals(a, b);
    }

    private static FeaturePlanState minimalPlan() {
        return new FeaturePlanState(
                "c",
                "f",
                "s",
                "room",
                null,
                null,
                "t",
                "req",
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
