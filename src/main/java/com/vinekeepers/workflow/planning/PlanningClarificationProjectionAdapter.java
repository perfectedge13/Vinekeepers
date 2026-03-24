package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.workflow.UnresolvedItemLedger;

import java.util.Locale;
import java.util.Map;

/**
 * Clarification-related workflow spread keys from ledger merge and evaluation projection. Does not rank gaps or derive
 * questions; consumes {@link PlanningEvaluationDecision} and {@link ClarificationProjection} as already finalized.
 */
public final class PlanningClarificationProjectionAdapter {

    public static final String CLARIFICATION_CONFIDENCE_SCORE_KEY = "planningClarificationConfidenceScore";
    public static final String CLARIFICATION_CONFIDENCE_HIGH_KEY = "planningClarificationConfidenceHigh";

    private PlanningClarificationProjectionAdapter() {}

    /**
     * Merge deliberation ledger and write clarification spread keys produced by the evaluation path (before canonical
     * persistence and {@link PlanningCanonicalDecisionSupport#projectToSpread}).
     */
    public static void applyPreCanonicalEvaluationClarification(
            Map<String, Object> spread,
            PlanningDeliberationLedgerSync.UpsertResult upsert,
            ClarificationProjection projection,
            PlanningEvaluationDecision decision) {
        UnresolvedItemLedger.mergeLedgerIntoSpread(spread, upsert.ledger());
        spread.put("planningClarificationLedgerItemId", upsert.activeItemId().orElse(""));
        spread.put("planningCanonicalUserInputRequired", decision.askUserRequired() ? "true" : "false");
        spread.put("planningClarificationChoicesJson", projection.choicesJson());
        spread.put("planningClarificationMetaJson", projection.metaJson());
        spread.put("planningClarificationUseStructuredChoices", projection.useStructuredChoices() ? "true" : "false");
        spread.put("planningClarificationQuestionText", projection.questionText() != null ? projection.questionText() : "");
        spread.put(
                "planningClarificationOrchestratorPrompt",
                projection.orchestratorPrompt() != null ? projection.orchestratorPrompt() : "");
        spread.put(
                CLARIFICATION_CONFIDENCE_SCORE_KEY,
                String.format(Locale.ROOT, "%.3f", decision.confidence().score() / 100.0));
        spread.put(
                CLARIFICATION_CONFIDENCE_HIGH_KEY,
                decision.confidence().score() >= 67 ? "true" : "false");
    }

    /**
     * After {@link PlanningCanonicalDecisionSupport#projectCanonicalCoreToSpread}, restore structured clarification fields
     * for {@code ASK_USER} from the builder projection (canonical carries question text only).
     */
    public static void applyStructuredClarificationOverlayForAskUser(
            Map<String, Object> spread, ClarificationProjection projection) {
        if (projection == null) {
            return;
        }
        spread.put("planningClarificationChoicesJson", projection.choicesJson());
        spread.put("planningClarificationMetaJson", projection.metaJson());
        spread.put("planningClarificationUseStructuredChoices", projection.useStructuredChoices() ? "true" : "false");
        spread.put("planningClarificationQuestionText", projection.questionText() != null ? projection.questionText() : "");
        spread.put(
                "planningClarificationOrchestratorPrompt",
                projection.orchestratorPrompt() != null ? projection.orchestratorPrompt() : "");
    }

    /**
     * Spread keys derived only from a persisted {@link com.vinekeepers.state.planning.PlanningCanonicalDecision} (hydrate,
     * critique, and shared {@link PlanningCanonicalDecisionSupport#projectToSpread} path).
     */
    public static void applyFromCanonicalDecision(
            Map<String, Object> spread,
            com.vinekeepers.state.planning.PlanningCanonicalDecision decision) {
        if (spread == null || decision == null) {
            return;
        }
        if (decision.nextAction() == com.vinekeepers.state.planning.PlanningCanonicalNextAction.ASK_USER) {
            spread.put("planningClarificationQuestionText", decision.questionText());
        } else {
            spread.put("planningClarificationQuestionText", "");
            spread.put("planningClarificationChoicesJson", "[]");
            spread.put("planningClarificationMetaJson", "{}");
            spread.put("planningClarificationOrchestratorPrompt", "");
            spread.put("planningClarificationUseStructuredChoices", "false");
        }
    }
}
