package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.FeaturePlanState;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanningCyclePipelinePhaseContextTest {

    @Test
    void firstPassUsesStartupEvaluationLabel() {
        FeaturePlanState plan = PlanningEvaluationServiceTest.minimalCoherentPlan();
        Map<String, Object> state = new LinkedHashMap<>();
        assertEquals("planning_startup_evaluation", PlanningCyclePipeline.resolvePlanningEvaluationPhaseContext(plan, state));
    }

    @Test
    void mergedClarificationUsesClarificationLabel() {
        FeaturePlanState plan = PlanningEvaluationServiceTest.minimalCoherentPlan();
        Map<String, Object> state = Map.of("planningJustMergedClarification", "true");
        assertEquals("planning_clarification", PlanningCyclePipeline.resolvePlanningEvaluationPhaseContext(plan, state));
    }

    @Test
    void afterSurfacedClarificationUsesClarificationLabel() {
        FeaturePlanState plan =
                PlanningEvaluationServiceTest.minimalCoherentPlan().withClarificationQuestionSurfaced("test surfacing");
        assertEquals("planning_clarification", PlanningCyclePipeline.resolvePlanningEvaluationPhaseContext(plan, Map.of()));
    }
}
