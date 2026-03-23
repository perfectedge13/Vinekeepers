package com.vinekeepers.workflow.planreview;

import com.vinekeepers.state.planning.DiscoveryGap;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanIssue;
import com.vinekeepers.state.planning.PlanConfidence;
import com.vinekeepers.state.planning.PlanCritiqueFinding;
import com.vinekeepers.state.planning.PlanReadinessStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanReadinessEvaluatorTest {

    private static final Instant T = Instant.parse("2026-03-20T12:00:00Z");

    @Test
    void blockedWhenBlockerGap() {
        List<DiscoveryGap> gaps = List.of(new DiscoveryGap(
                "g1", "WORKSPACE", "", "", "", "bad", "BLOCKER", "OPEN", "", ""));
        PlanConfidence c = PlanReadinessEvaluator.evaluate(minPlan(), gaps, List.of(), T);
        assertEquals(PlanReadinessStatus.BLOCKED, c.getReadinessStatus());
    }

    @Test
    void needsRevisionWhenOpenGap() {
        List<DiscoveryGap> gaps = List.of(new DiscoveryGap(
                "g1", "REQUIRED_FIELD", "a", "s", "f", "missing", "HIGH", "OPEN", "", ""));
        PlanConfidence c = PlanReadinessEvaluator.evaluate(minPlan(), gaps, List.of(), T);
        assertEquals(PlanReadinessStatus.NOT_READY, c.getReadinessStatus());
    }

    @Test
    void needsHumanWhenIssuesPresent() {
        FeaturePlanState p =
                posted(minPlan()).withAppendedIssue(PlanIssue.fromLegacyText("i1", "risk", T));
        PlanConfidence c = PlanReadinessEvaluator.evaluate(p, List.of(), List.of(), T);
        assertEquals(PlanReadinessStatus.CONDITIONALLY_READY, c.getReadinessStatus());
    }

    @Test
    void readyWhenClean() {
        PlanConfidence c = PlanReadinessEvaluator.evaluate(posted(minPlan()), List.of(), List.of(), T);
        assertEquals(PlanReadinessStatus.READY, c.getReadinessStatus());
        assertEquals("HIGH", c.getLevel());
    }

    @Test
    void needsRevisionWhenMustFixFinding() {
        List<PlanCritiqueFinding> f = List.of(new PlanCritiqueFinding(
                "1", "PROCESS", "MUST_FIX", "X", "fix profile", ""));
        PlanConfidence c = PlanReadinessEvaluator.evaluate(minPlan(), List.of(), f, T);
        assertEquals(PlanReadinessStatus.NOT_READY, c.getReadinessStatus());
    }

    @Test
    void readyWhenPacketPostedInferredFromWorkflowVersion() {
        PlanConfidence c = PlanReadinessEvaluator.evaluate(
                minPlan(), List.of(), List.of(), T, Map.of("planningPacketPostedVersion", 1));
        assertEquals(PlanReadinessStatus.READY, c.getReadinessStatus());
    }

    @Test
    void inferPacketPosted_prefersPlanOverWorkflow() {
        assertTrue(PlanReadinessEvaluator.inferPacketPostedOnPlan(
                posted(minPlan()), Map.of("planningPacketPosted", "false")));
    }

    @Test
    void inferPacketPosted_falseWithoutPlanTimestampOrWorkflowSignals() {
        assertFalse(PlanReadinessEvaluator.inferPacketPostedOnPlan(minPlan(), null));
        assertFalse(PlanReadinessEvaluator.inferPacketPostedOnPlan(minPlan(), Map.of()));
    }

    @Test
    void inferPacketPosted_trueFromWorkflowBooleanString() {
        assertTrue(PlanReadinessEvaluator.inferPacketPostedOnPlan(
                minPlan(), Map.of("planningPacketPosted", "TRUE")));
    }

    @Test
    void inferPacketPosted_invalidVersionStringIsFalse() {
        assertFalse(PlanReadinessEvaluator.inferPacketPostedOnPlan(
                minPlan(), Map.of("planningPacketPostedVersion", "nope")));
    }

    /** Readiness treats an unposted packet as not ready; mirror post step recording on the plan. */
    private static FeaturePlanState posted(FeaturePlanState plan) {
        return plan.withPacketPosted(T, "", "test-fp", 1);
    }

    private static FeaturePlanState minPlan() {
        return new FeaturePlanState(
                "ctx",
                "f",
                "s",
                "room",
                null,
                null,
                "t",
                null,
                "PLANNING",
                null,
                null,
                null,
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
                Map.of(),
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
}
