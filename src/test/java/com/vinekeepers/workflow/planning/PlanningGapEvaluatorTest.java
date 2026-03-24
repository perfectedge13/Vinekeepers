package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.CoordinatorClarificationMode;
import com.vinekeepers.profile.CoordinatorClarificationSettings;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.state.workflow.UnresolvedItem;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.state.workflow.UnresolvedItemStatus;
import com.vinekeepers.workflow.planning.ClarificationProjection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.vinekeepers.workflow.planning.PlanningGapEvaluator.PLANNING_CLARIFICATION_CHANNEL;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PlanningGapEvaluatorTest {

    @Test
    void effectiveRankedCanonicalV1DoesNotRehydrateFromStaleLedgerOpen() {
        UnresolvedItem staleOpen =
                new UnresolvedItem(
                        "uq_1",
                        "fp",
                        UnresolvedItemStatus.OPEN,
                        "",
                        "Stale question from ledger",
                        "normal",
                        Map.of("channel", PLANNING_CLARIFICATION_CHANNEL, "inputKind", "open"),
                        List.of(),
                        List.of(),
                        0);
        UnresolvedItemLedger ledger = UnresolvedItemLedger.empty().withAdded(staleOpen);
        ClarificationProjection rankedFromLlm =
                new ClarificationProjection(false, "", "[]", "{}", 0, List.of(), false, "");
        WorkProfileDefinition profile =
                new WorkProfileDefinition(
                        "p",
                        "",
                        List.of(),
                        List.of(),
                        false,
                        false,
                        List.of(),
                        new CoordinatorClarificationSettings(CoordinatorClarificationMode.CANONICAL_V1, List.of()));
        ClarificationProjection out =
                LegacyPlanningGapSupport.effectiveRanked(null, ledger, rankedFromLlm, profile, profile.getCoordinatorClarification());
        assertFalse(out.userInputRequired());
    }
}
