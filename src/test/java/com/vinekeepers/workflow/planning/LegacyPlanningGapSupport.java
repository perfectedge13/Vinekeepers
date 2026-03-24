package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.CoordinatorClarificationSettings;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.workflow.UnresolvedItem;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;

import java.util.ArrayList;
import java.util.List;

/**
 * Preserves pre-cutover ledger rehydration behavior for tests only (removed from {@link PlanningGapEvaluator} in main).
 */
final class LegacyPlanningGapSupport {

    private LegacyPlanningGapSupport() {}

    static ClarificationProjection effectiveRanked(
            FeaturePlanState plan,
            UnresolvedItemLedger ledgerAfterUpsert,
            ClarificationProjection rankedFromLlm,
            WorkProfileDefinition profile,
            CoordinatorClarificationSettings coordinatorClarification) {
        if (ledgerAfterUpsert == null || rankedFromLlm == null) {
            return rankedFromLlm;
        }
        if (coordinatorClarification != null && coordinatorClarification.isCanonicalV1()) {
            return rankedFromLlm;
        }
        if (!PlanningGapEvaluator.requiresUserInputForPlanningClarification(ledgerAfterUpsert)) {
            return rankedFromLlm;
        }
        if (rankedFromLlm.userInputRequired()) {
            return rankedFromLlm;
        }
        var open = PlanningGapEvaluator.firstOpenPlanningClarification(ledgerAfterUpsert);
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
        ClarificationProjection rehydrated =
                PlanningQuestionRankingPolicy.rank(
                        plan, List.of(q), 1, ledgerAfterUpsert, allowBoundedPass, applyOrText);
        List<String> assumptions = new ArrayList<>(rankedFromLlm.assumptionsToRecord());
        assumptions.addAll(rehydrated.assumptionsToRecord());
        int blocking =
                "blocking".equalsIgnoreCase(it.getSeverity())
                        ? Math.max(1, rehydrated.blockingQuestionCount())
                        : rehydrated.blockingQuestionCount();
        return new ClarificationProjection(
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
