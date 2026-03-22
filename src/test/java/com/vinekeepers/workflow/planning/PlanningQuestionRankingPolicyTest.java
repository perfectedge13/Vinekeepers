package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.FeaturePlanState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningQuestionRankingPolicyTest {

    @Test
    void resolvesLlmOverrideQuestionToAssumptionWithoutUser() {
        FeaturePlanState plan = null;
        var r = PlanningQuestionRankingPolicy.rank(
                plan,
                List.of("Should each LLM-capable workflow step override both provider and model?"),
                3);
        assertFalse(r.userInputRequired());
        assertFalse(r.assumptionsToRecord().isEmpty());
    }

    @Test
    void lowValueQuestionFilteredOut() {
        var r = PlanningQuestionRankingPolicy.rank(null, List.of("ok"), 3);
        assertFalse(r.userInputRequired());
    }

    @Test
    void implementationQuestionDefaultsToOpenTextWithoutStructuredChoices() {
        var r = PlanningQuestionRankingPolicy.rank(
                null,
                List.of(
                        "Should we implement runtime model resolution in Bootstrap while keeping YAML as the source of truth for per-step models?"),
                3);
        assertTrue(r.userInputRequired());
        assertFalse(r.useStructuredChoices());
        assertEquals("[]", r.choicesJson());
    }

    @Test
    void explicitOrPairSurfacesStructuredChoices() {
        var r = PlanningQuestionRankingPolicy.rank(
                null,
                List.of("Should we implement the session store using PostgreSQL or SQLite for this feature?"),
                3);
        assertTrue(r.userInputRequired());
        assertTrue(r.useStructuredChoices());
        assertTrue(r.choicesJson().contains("planning_clarify_default"));
        assertTrue(r.choicesJson().contains("planning_clarify_opt_a"));
    }

    @Test
    void commaClauseWithOrStaysOpenTextNotStructuredChoices() {
        var r = PlanningQuestionRankingPolicy.rank(
                null,
                List.of(
                        "Should we treat routing as config-driven at first, or must runtime context always win when both are present?"),
                3);
        assertTrue(r.userInputRequired());
        assertFalse(r.useStructuredChoices());
        assertEquals("[]", r.choicesJson());
    }
}
