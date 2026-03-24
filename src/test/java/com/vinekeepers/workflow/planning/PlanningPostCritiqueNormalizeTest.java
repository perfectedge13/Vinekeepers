package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanCritiqueFinding;
import com.vinekeepers.state.planning.PlanGovernanceSeverity;
import com.vinekeepers.state.planning.PlanIssue;
import com.vinekeepers.state.planning.PlanIssueStatus;
import com.vinekeepers.state.planning.PlanReadinessStatus;
import com.vinekeepers.state.planning.PlanningCanonicalNextAction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PlanningPostCritiqueNormalizeTest {

    @Test
    void notReadyWithMaterialChangeDoesNotFailCloseToBlock() {
        FeaturePlanState plan = PlanningEvaluationServiceTest.minimalCoherentPlan();
        var canonical =
                PlanningCanonicalDecisionSupport.normalizePostCritique(
                        plan,
                        PlanReadinessStatus.NOT_READY,
                        false,
                        false,
                        "",
                        "fresh-material-fingerprint",
                        "needs another revision internally",
                        "",
                        "");

        assertNotEquals(PlanningCanonicalNextAction.BLOCK, canonical.nextAction());
        assertEquals(PlanningCanonicalNextAction.READY_FOR_PACKET, canonical.nextAction());
    }

    @Test
    void wantsClarificationWithoutQuestionDoesNotBlock() {
        FeaturePlanState plan = PlanningEvaluationServiceTest.minimalCoherentPlan();
        var canonical =
                PlanningCanonicalDecisionSupport.normalizePostCritique(
                        plan,
                        PlanReadinessStatus.NOT_READY,
                        true,
                        false,
                        "",
                        "fp",
                        "",
                        "",
                        "");

        assertNotEquals(PlanningCanonicalNextAction.BLOCK, canonical.nextAction());
        assertEquals(PlanningCanonicalNextAction.READY_FOR_PACKET, canonical.nextAction());
    }

    @Test
    void critiqueFindingsAloneDoNotForceBlockWhenNotOtherwiseBlocked() {
        FeaturePlanState plan = PlanningEvaluationServiceTest.minimalCoherentPlan();
        List<PlanCritiqueFinding> findings =
                List.of(
                        new PlanCritiqueFinding(
                                "c1",
                                "QUALITY",
                                "SHOULD_FIX",
                                "THIN_VALIDATION",
                                "Add integration tests for the new path.",
                                ""));
        FeaturePlanState annotated = applyCritiqueFindingsToPlanIssuesForTest(plan, findings);
        var canonical =
                PlanningCanonicalDecisionSupport.normalizePostCritique(
                        annotated,
                        PlanReadinessStatus.NOT_READY,
                        false,
                        false,
                        "",
                        "fp2",
                        "",
                        "",
                        "");

        assertNotEquals(PlanningCanonicalNextAction.BLOCK, canonical.nextAction());
    }

    /** Mirrors production append logic (kept in test to avoid widening action API). */
    private static FeaturePlanState applyCritiqueFindingsToPlanIssuesForTest(
            FeaturePlanState plan, List<PlanCritiqueFinding> findings) {
        if (plan == null || findings == null || findings.isEmpty()) {
            return plan;
        }
        FeaturePlanState out = plan;
        for (PlanCritiqueFinding f : findings) {
            String id = "critique-finding-" + f.getId();
            String msg = f.getMessage() != null ? f.getMessage().trim() : "";
            if (msg.isBlank()) {
                continue;
            }
            String title =
                    f.getCode() != null && !f.getCode().isBlank()
                            ? f.getCode()
                            : (f.getCategory() != null ? f.getCategory() : "Critique");
            out =
                    out.withAppendedIssue(
                            new PlanIssue(
                                    id,
                                    title,
                                    msg,
                                    PlanIssueStatus.OPEN,
                                    PlanGovernanceSeverity.MEDIUM,
                                    "PLAN_CRITIQUE",
                                    "",
                                    f.getRelatedFieldKeys(),
                                    java.time.Instant.now(),
                                    java.time.Instant.now()));
        }
        return out;
    }
}
