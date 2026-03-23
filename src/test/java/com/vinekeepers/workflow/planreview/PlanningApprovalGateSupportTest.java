package com.vinekeepers.workflow.planreview;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanConfidence;
import com.vinekeepers.state.planning.PlanCritiqueLifecycleStatus;
import com.vinekeepers.state.planning.PlanCritiqueRubricScores;
import com.vinekeepers.state.planning.PlanCritiqueSnapshot;
import com.vinekeepers.state.planning.PlanReadinessStatus;
import com.vinekeepers.state.planning.PlanningIntakeStage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningApprovalGateSupportTest {

    private static final Instant T = Instant.parse("2026-03-20T12:00:00Z");

    @Test
    void validateApproveAllowed_blocksWhenConfidenceBelowThreshold() {
        FeaturePlanState plan = approvalBasePlan(0.84);
        String block = PlanningApprovalGateSupport.validateApproveAllowed(plan, Map.of("planningPacketPostedVersion", 1));
        assertNotNull(block);
        assertTrue(block.contains("below the threshold"));
        assertTrue(block.contains(String.valueOf(PlanReadinessCalculator.APPROVAL_CONFIDENCE_THRESHOLD)));
    }

    @Test
    void validateApproveAllowed_allowsAtThreshold() {
        FeaturePlanState plan = approvalBasePlan(PlanReadinessCalculator.APPROVAL_CONFIDENCE_THRESHOLD);
        assertNull(PlanningApprovalGateSupport.validateApproveAllowed(plan, Map.of("planningPacketPostedVersion", 1)));
    }

    @Test
    void validateApproveAllowed_requiresHumanAckForConditionalReadiness() {
        FeaturePlanState plan = approvalBasePlan(PlanReadinessCalculator.APPROVAL_CONFIDENCE_THRESHOLD)
                .withPlanConfidence(new PlanConfidence(
                        "HIGH",
                        "conditional",
                        PlanReadinessStatus.CONDITIONALLY_READY,
                        T,
                        PlanReadinessCalculator.APPROVAL_CONFIDENCE_THRESHOLD,
                        List.of()));
        String block = PlanningApprovalGateSupport.validateApproveAllowed(plan, Map.of("planningPacketPostedVersion", 1));
        assertNotNull(block);
        assertTrue(block.contains("human checkpoint"));
    }

    @Test
    void validateApproveAllowed_blocksWhenOnlyReviewable() {
        FeaturePlanState plan = approvalBasePlan(0.7)
                .withPlanConfidence(new PlanConfidence(
                        "MEDIUM",
                        "reviewable",
                        PlanReadinessStatus.REVIEWABLE,
                        T,
                        0.7,
                        List.of()));
        String block = PlanningApprovalGateSupport.validateApproveAllowed(plan, Map.of("planningPacketPostedVersion", 1));
        assertNotNull(block);
        assertTrue(block.contains("REVIEWABLE"));
    }

    @Test
    void validateApproveAllowed_allowsConditionalReadinessAfterHumanAck() {
        FeaturePlanState plan = approvalBasePlan(PlanReadinessCalculator.APPROVAL_CONFIDENCE_THRESHOLD)
                .withPlanConfidence(new PlanConfidence(
                        "HIGH",
                        "conditional",
                        PlanReadinessStatus.CONDITIONALLY_READY,
                        T,
                        PlanReadinessCalculator.APPROVAL_CONFIDENCE_THRESHOLD,
                        List.of()));
        assertNull(
                PlanningApprovalGateSupport.validateApproveAllowed(
                        plan,
                        Map.of(
                                "planningPacketPostedVersion", 1,
                                "planningHumanReadinessAcknowledged", "true")));
    }

    private static FeaturePlanState approvalBasePlan(double confidenceScore) {
        PlanCritiqueRubricScores rubric =
                new PlanCritiqueRubricScores(0.9, 0.9, 0.9, 0.9, 0.9, 0.9, 0.9);
        PlanCritiqueSnapshot snap =
                new PlanCritiqueSnapshot(
                        T,
                        "RULES_V1",
                        List.of(),
                        PlanCritiqueLifecycleStatus.COMPLETE,
                        rubric,
                        0,
                        List.of());
        PlanConfidence conf =
                new PlanConfidence("HIGH", "ok", PlanReadinessStatus.READY, T, confidenceScore, List.of());
        FeaturePlanState base =
                new FeaturePlanState(
                        "c-approve",
                        "f",
                        "s",
                        "room",
                        null,
                        null,
                        "t",
                        "1234567890123456789012345678901234567890",
                        "PLANNING",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        FeaturePlanState.initialSectionStatuses(),
                        conf,
                        null,
                        snap,
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
                        T,
                        T);
        return base.withPacketPosted(T, "", "fp", 1)
                .withPlanningIntakeStage(PlanningIntakeStage.PACKET_POSTED, null)
                .withCritiqueLifecycleStatus(PlanCritiqueLifecycleStatus.COMPLETE);
    }
}
