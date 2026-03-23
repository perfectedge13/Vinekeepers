package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.CoordinatorClarificationSettings;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.workflow.UnresolvedItem;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.state.workflow.UnresolvedItemStatus;
import com.vinekeepers.workflow.planning.PlanningQuestionRankingPolicy.RankedClarification;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Clarification gating: after {@link PlanningDeliberationLedgerSync} upsert, legacy profiles derive
 * {@code planningUserInputRequired} from OPEN ledger rows ({@link #requiresUserInputForPlanningClarification}). In
 * {@link com.vinekeepers.profile.CoordinatorClarificationMode#CANONICAL_V1},
 * {@link com.vinekeepers.workflow.planning.PlanningCyclePipeline} sets {@code planningUserInputRequired} from canonical gap
 * evaluation (non-empty open gap set), not from OPEN rows first;
 * {@link #effectiveRanked} does not rehydrate from stale ledger rows — reconcile runs from evaluator output first.
 */
public final class PlanningGapEvaluator {

    public static final String PLANNING_CLARIFICATION_CHANNEL = "planning_clarification";

    private PlanningGapEvaluator() {}

    /**
     * True when the ledger has at least one OPEN planning-clarification item with non-empty question text. Used for legacy
     * coordinator mode; canonical_v1 uses evaluator-driven pending in {@code PlanningCyclePipeline}.
     */
    public static boolean requiresUserInputForPlanningClarification(UnresolvedItemLedger ledger) {
        return firstOpenPlanningClarification(ledger).isPresent();
    }

    /**
     * First OPEN item tagged as planning coordinator clarification.
     */
    public static Optional<UnresolvedItem> firstOpenPlanningClarification(UnresolvedItemLedger ledger) {
        if (ledger == null) {
            return Optional.empty();
        }
        for (UnresolvedItem it : ledger.items()) {
            if (it.getStatus() != UnresolvedItemStatus.OPEN) {
                continue;
            }
            if (!PLANNING_CLARIFICATION_CHANNEL.equals(it.getSource().get("channel"))) {
                continue;
            }
            if (it.getQuestionText() == null || it.getQuestionText().isBlank()) {
                continue;
            }
            return Optional.of(it);
        }
        return Optional.empty();
    }

    /**
     * Merge LLM-ranked clarification with ledger state: if the ledger still has an OPEN planning item but this
     * cycle's ranker did not surface a question, rehydrate UI fields from the ledger item (and re-run bounded
     * inference only when the profile and item {@code inputKind} allow it).
     */
    public static RankedClarification effectiveRanked(
            FeaturePlanState plan,
            UnresolvedItemLedger ledgerAfterUpsert,
            RankedClarification rankedFromLlm,
            WorkProfileDefinition profile) {
        CoordinatorClarificationSettings coord =
                profile != null ? profile.getCoordinatorClarification() : CoordinatorClarificationSettings.legacyDefault();
        return effectiveRanked(plan, ledgerAfterUpsert, rankedFromLlm, profile, coord);
    }

    public static RankedClarification effectiveRanked(
            FeaturePlanState plan,
            UnresolvedItemLedger ledgerAfterUpsert,
            RankedClarification rankedFromLlm,
            WorkProfileDefinition profile,
            CoordinatorClarificationSettings coordinatorClarification) {
        if (ledgerAfterUpsert == null || rankedFromLlm == null) {
            return rankedFromLlm;
        }
        if (coordinatorClarification != null && coordinatorClarification.isCanonicalV1()) {
            return rankedFromLlm;
        }
        if (!requiresUserInputForPlanningClarification(ledgerAfterUpsert)) {
            return rankedFromLlm;
        }
        if (rankedFromLlm.userInputRequired()) {
            return rankedFromLlm;
        }
        Optional<UnresolvedItem> open = firstOpenPlanningClarification(ledgerAfterUpsert);
        if (open.isEmpty()) {
            return rankedFromLlm;
        }
        UnresolvedItem it = open.get();
        String q = it.getQuestionText().trim();
        if (q.isBlank()) {
            return rankedFromLlm;
        }
        boolean profileBounded = profile != null && profile.isBoundedClarificationChoicesEnabled();
        boolean orHeuristic = profile != null && profile.isInferBoundedChoiceFromOrInTextEnabled();
        String inputKind = it.getSource().get("inputKind");
        boolean allowBoundedPass =
                profileBounded && ("bounded_choice".equalsIgnoreCase(inputKind) || orHeuristic);
        boolean applyOrText = orHeuristic || "bounded_choice".equalsIgnoreCase(inputKind);
        RankedClarification rehydrated =
                PlanningQuestionRankingPolicy.rank(
                        plan, List.of(q), 1, ledgerAfterUpsert, allowBoundedPass, applyOrText);
        List<String> assumptions = new ArrayList<>(rankedFromLlm.assumptionsToRecord());
        assumptions.addAll(rehydrated.assumptionsToRecord());
        int blocking =
                "blocking".equalsIgnoreCase(it.getSeverity())
                        ? Math.max(1, rehydrated.blockingQuestionCount())
                        : rehydrated.blockingQuestionCount();
        return new RankedClarification(
                true,
                rehydrated.orchestratorPrompt(),
                rehydrated.choicesJson(),
                rehydrated.metaJson(),
                blocking,
                assumptions,
                rehydrated.useStructuredChoices(),
                rehydrated.questionText());
    }
}
