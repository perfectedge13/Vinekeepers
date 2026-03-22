package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.FeaturePlanState;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void implementationQuestionSurfacesWithChoices() {
        var r = PlanningQuestionRankingPolicy.rank(
                null,
                List.of(
                        "Should we implement runtime model resolution in Bootstrap or only YAML workflow metadata for per-step models?"),
                3);
        assertTrue(r.userInputRequired());
        assertTrue(r.choicesJson().contains("planning_clarify_default"));
    }
}
