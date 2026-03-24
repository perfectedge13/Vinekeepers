package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.workflow.UnresolvedItem;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.state.workflow.UnresolvedItemStatus;

import java.util.Optional;

/**
 * Clarification gating: after {@link PlanningDeliberationLedgerSync} upsert, OPEN ledger rows drive
 * {@link #requiresUserInputForPlanningClarification} for diagnostics. Live {@code arrietty_room_v2} uses
 * {@link CanonicalPlanningGapEngine} + canonical ledger reconciliation only ({@code canonical_v1} required).
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

}
