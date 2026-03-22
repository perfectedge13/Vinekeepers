package com.vinekeepers.workflow.planreview;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanConfidence;
import com.vinekeepers.state.planning.PlanCritiqueFinding;
import com.vinekeepers.state.planning.PlanCritiqueRubricScores;
import com.vinekeepers.state.planning.PlanGovernanceSeverity;
import com.vinekeepers.state.planning.PlanIssue;
import com.vinekeepers.state.planning.PlanIssueStatus;
import com.vinekeepers.state.planning.PlanReadinessStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanReadinessCalculatorTest {

    private static final Instant T = Instant.parse("2026-03-20T12:00:00Z");

    private static FeaturePlanState basePlan(List<PlanIssue> issues) {
        return new FeaturePlanState(
                "c",
                "f",
                "s",
                "r",
                null,
                null,
                "t",
                "1234567890123456789012345678901234567890ABCD",
                "PLANNING",
                null,
                null,
                issues,
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
                "software_feature_planning",
                java.util.Map.of(),
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

    @Test
    void blockingIssueForcesNotReady() {
        PlanIssue bi =
                new PlanIssue(
                        "i1",
                        "block",
                        "",
                        PlanIssueStatus.BLOCKING,
                        PlanGovernanceSeverity.HIGH,
                        "T",
                        "",
                        List.of(),
                        T,
                        T);
        FeaturePlanState plan = basePlan(List.of(bi)).withPacketPosted(T, "", "fp", 1);
        PlanCritiqueRubricScores rubric = new PlanCritiqueRubricScores(0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9);
        PlanConfidence c =
                PlanReadinessCalculator.evaluate(
                        plan, List.of(), List.of(), rubric, T, true);
        assertEquals(PlanReadinessStatus.NOT_READY, c.getReadinessStatus());
    }

    @Test
    void cleanPlanReadyWhenPacketPosted() {
        FeaturePlanState plan = basePlan(null).withPacketPosted(T, "", "fp", 1);
        PlanCritiqueRubricScores rubric = new PlanCritiqueRubricScores(0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95);
        PlanConfidence c =
                PlanReadinessCalculator.evaluate(plan, List.of(), List.of(), rubric, T, true);
        assertEquals(PlanReadinessStatus.READY, c.getReadinessStatus());
    }

    @Test
    void blockingCritiqueFindingForcesNotReady() {
        FeaturePlanState plan = basePlan(null).withPacketPosted(T, "", "fp", 1);
        List<PlanCritiqueFinding> f =
                List.of(
                        new PlanCritiqueFinding(
                                "f1", "X", "MUST_FIX", "C", "fix", "ref", true, List.of()));
        PlanCritiqueRubricScores rubric = PlanCritiqueRubric.compute(plan, f, 1);
        PlanConfidence c = PlanReadinessCalculator.evaluate(plan, List.of(), f, rubric, T, true);
        assertEquals(PlanReadinessStatus.NOT_READY, c.getReadinessStatus());
    }
}
