package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanConfidence;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningEvaluationServiceTest {

    @Test
    void applyEvaluationDeltas_appendsGovernanceRowsAndConfidence() {
        PlanningEvaluationService service = new PlanningEvaluationService(null);
        FeaturePlanState plan = minimalPlan();
        PlanningEvaluationDecision decision =
                new PlanningEvaluationDecision(
                        true,
                        com.vinekeepers.state.planning.PlanningCanonicalNextAction.READY_FOR_PACKET,
                        com.vinekeepers.state.planning.PlanningIntakeStage.DRAFTING,
                        com.vinekeepers.state.planning.PlanningInteractionState.NONE,
                        "MATERIALIZED_NOT_INSPECTED",
                        new PlanningEvaluationDecision.EvaluationConfidence(78, "high", "Packet-ready after one assumption."),
                        List.of(),
                        false,
                        new PlanningEvaluationDecision.EvaluationBestQuestion("", ""),
                        "",
                        "",
                        List.of(new PlanningEvaluationDecision.AssumptionProposal("Assume repo defaults remain unchanged.", "MEDIUM")),
                        List.of(new PlanningEvaluationDecision.IssueProposal("Flag contradiction", "Docs and code disagree.", "HIGH", true)),
                        List.of(new PlanningEvaluationDecision.RiskProposal("Rollout could surprise admins.", "HIGH", "MEDIUM")),
                        List.of(new PlanningEvaluationDecision.DecisionProposal("Ship behind a flag.", "Limits blast radius.", "OPEN")),
                        true,
                        true,
                        false,
                        false,
                        "",
                        "Packet-ready after one assumption.",
                        false,
                        false,
                        "");

        FeaturePlanState next = service.applyEvaluationDeltas(plan, decision);

        assertEquals(1, next.getAssumptions().size());
        assertEquals(1, next.getIssues().size());
        assertEquals(1, next.getRisks().size());
        assertEquals(1, next.getDecisions().size());
        PlanConfidence confidence = next.getPlanConfidence();
        assertNotNull(confidence);
        assertEquals("high", confidence.getLevel());
        assertTrue(confidence.getConfidenceScore() > 0.7);
    }

    @Test
    void chosenAskGap_returnsFirstEligibleBlockingOrBranchingGap() {
        PlanningEvaluationDecision decision =
                new PlanningEvaluationDecision(
                        true,
                        com.vinekeepers.state.planning.PlanningCanonicalNextAction.ASK_USER,
                        com.vinekeepers.state.planning.PlanningIntakeStage.CLARIFYING,
                        com.vinekeepers.state.planning.PlanningInteractionState.WAITING_FOR_TEXT_REPLY,
                        "MATERIALIZED_NOT_INSPECTED",
                        new PlanningEvaluationDecision.EvaluationConfidence(42, "medium", "Needs one decision."),
                        List.of(
                                CanonicalPlanningGap.fromEvaluation(
                                        "missing_authority",
                                        "MISSING_AUTHORITY",
                                        "Need product authority for rollout scope.",
                                        true,
                                        true,
                                        false,
                                        "",
                                        List.of()),
                                CanonicalPlanningGap.fromEvaluation(
                                        "weak_validation",
                                        "WEAK_VALIDATION",
                                        "Validation depth is still light.",
                                        false,
                                        false,
                                        true,
                                        "",
                                        List.of())),
                        true,
                        new PlanningEvaluationDecision.EvaluationBestQuestion(
                                "Who can approve the rollout boundary for this change?",
                                "This authority unlocks packet readiness."),
                        "missing_authority",
                        "Need product authority for rollout scope.",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        false,
                        false,
                        false,
                        false,
                        "",
                        "Needs one decision.",
                        false,
                        false,
                        "");

        CanonicalPlanningGap chosen = decision.chosenAskGap();

        assertNotNull(chosen);
        assertEquals("missing_authority", chosen.gapId());
        assertTrue(decision.askUserRequired());
        assertFalse(decision.readyForPacket());
    }

    @Test
    void parseAndValidate_rejectsDuplicateGapIds() throws Exception {
        PlanningEvaluationService service = new PlanningEvaluationService(null);

        PlanningEvaluationDecision result =
                parseAndValidate(
                        service,
                        """
                        {
                          "confidence": { "score": 40, "level": "medium", "summary": "Needs work." },
                          "gaps": [
                            { "id": "dup_gap", "kind": "MISSING_AUTHORITY", "description": "Need approver.", "blocking": true, "askable": true, "assumable": false },
                            { "id": "dup_gap", "kind": "WEAK_VALIDATION", "description": "Validation unclear.", "blocking": false, "askable": false, "assumable": true }
                          ],
                          "ask_user_required": true,
                          "best_question": { "text": "Who approves this rollout?", "rationale": "Unlocks progress." },
                          "assumptions_to_add": [],
                          "issues_to_add": [],
                          "risks_to_add": [],
                          "decisions_to_add": [],
                          "ready_for_packet": false
                        }
                        """);

        assertFalse(result.success());
        assertEquals("EVALUATION_INVALID_DUPLICATE_GAP_ID", result.machineError());
    }

    @Test
    void parseAndValidate_rejectsMultipleEligibleAskGaps() throws Exception {
        PlanningEvaluationService service = new PlanningEvaluationService(null);

        PlanningEvaluationDecision result =
                parseAndValidate(
                        service,
                        """
                        {
                          "confidence": { "score": 38, "level": "medium", "summary": "Two questions compete." },
                          "gaps": [
                            { "id": "gap_one", "kind": "MISSING_AUTHORITY", "description": "Need approver.", "blocking": true, "askable": true, "assumable": false },
                            { "id": "gap_two", "kind": "BRANCHING_DECISION", "description": "Need direction choice.", "blocking": false, "askable": true, "assumable": false }
                          ],
                          "ask_user_required": true,
                          "best_question": { "text": "Who approves this rollout?", "rationale": "Only one question is allowed." },
                          "assumptions_to_add": [],
                          "issues_to_add": [],
                          "risks_to_add": [],
                          "decisions_to_add": [],
                          "ready_for_packet": false
                        }
                        """);

        assertFalse(result.success());
        assertEquals("EVALUATION_INVALID_MULTIPLE_ASK_GAPS", result.machineError());
    }

    @Test
    void parseAndValidate_rejectsAskUserWithoutEligibleGap() throws Exception {
        PlanningEvaluationService service = new PlanningEvaluationService(null);

        PlanningEvaluationDecision result =
                parseAndValidate(
                        service,
                        """
                        {
                          "confidence": { "score": 38, "level": "medium", "summary": "No askable blocking gap exists." },
                          "gaps": [
                            { "id": "gap_one", "kind": "WEAK_VALIDATION", "description": "Validation is thin.", "blocking": false, "askable": false, "assumable": true }
                          ],
                          "ask_user_required": true,
                          "best_question": { "text": "Who approves this rollout?", "rationale": "Should fail closed." },
                          "assumptions_to_add": [],
                          "issues_to_add": [],
                          "risks_to_add": [],
                          "decisions_to_add": [],
                          "ready_for_packet": false
                        }
                        """);

        assertFalse(result.success());
        assertEquals("EVALUATION_INVALID_NO_ELIGIBLE_ASK_GAP", result.machineError());
    }

    @Test
    void parseAndValidate_rejectsUnexpectedQuestionWhenAskIsFalse() throws Exception {
        PlanningEvaluationService service = new PlanningEvaluationService(null);

        PlanningEvaluationDecision result =
                parseAndValidate(
                        service,
                        """
                        {
                          "confidence": { "score": 72, "level": "high", "summary": "Ready without a user ask." },
                          "gaps": [],
                          "ask_user_required": false,
                          "best_question": { "text": "Should we still ask something?", "rationale": "Invalid when ask_user_required is false." },
                          "assumptions_to_add": [],
                          "issues_to_add": [],
                          "risks_to_add": [],
                          "decisions_to_add": [],
                          "ready_for_packet": false
                        }
                        """);

        assertFalse(result.success());
        assertEquals("EVALUATION_INVALID_UNEXPECTED_QUESTION", result.machineError());
    }

    @Test
    void parseAndValidate_rejectsConflictingAskAndReadyRoute() throws Exception {
        PlanningEvaluationService service = new PlanningEvaluationService(null);

        PlanningEvaluationDecision result =
                parseAndValidate(
                        service,
                        """
                        {
                          "confidence": { "score": 55, "level": "medium", "summary": "Conflicting route." },
                          "gaps": [
                            { "id": "gap_one", "kind": "MISSING_AUTHORITY", "description": "Need approver.", "blocking": true, "askable": true, "assumable": false }
                          ],
                          "ask_user_required": true,
                          "best_question": { "text": "Who approves this rollout?", "rationale": "Only question." },
                          "assumptions_to_add": [],
                          "issues_to_add": [],
                          "risks_to_add": [],
                          "decisions_to_add": [],
                          "ready_for_packet": true
                        }
                        """);

        assertFalse(result.success());
        assertEquals("EVALUATION_INVALID_CONFLICTING_ROUTE", result.machineError());
    }

    @Test
    void parseAndValidate_rejectsReadyForPacketWithBlockingGap() throws Exception {
        PlanningEvaluationService service = new PlanningEvaluationService(null);

        PlanningEvaluationDecision result =
                parseAndValidate(
                        service,
                        """
                        {
                          "confidence": { "score": 81, "level": "high", "summary": "Still blocked by one gap." },
                          "gaps": [
                            { "id": "gap_one", "kind": "MISSING_IMPLEMENTATION_SCOPE", "description": "Need scope.", "blocking": true, "askable": false, "assumable": false }
                          ],
                          "ask_user_required": false,
                          "best_question": { "text": "", "rationale": "" },
                          "assumptions_to_add": [],
                          "issues_to_add": [],
                          "risks_to_add": [],
                          "decisions_to_add": [],
                          "ready_for_packet": true
                        }
                        """);

        assertFalse(result.success());
        assertEquals("EVALUATION_INVALID_BLOCKING_READY", result.machineError());
    }

    @Test
    void parseAndValidate_packetRouteWhenNoBlockingGapsEvenIfModelWithholdsReady() throws Exception {
        PlanningEvaluationService service = new PlanningEvaluationService(null);

        PlanningEvaluationDecision result =
                parseAndValidate(
                        service,
                        """
                        {
                          "confidence": { "score": 65, "level": "medium", "summary": "Coherent without repo depth." },
                          "gaps": [
                            { "id": "weak_val", "kind": "WEAK_VALIDATION", "description": "Could add tests later.", "blocking": false, "askable": false, "assumable": true }
                          ],
                          "ask_user_required": false,
                          "best_question": { "text": "", "rationale": "" },
                          "assumptions_to_add": [],
                          "issues_to_add": [],
                          "risks_to_add": [],
                          "decisions_to_add": [],
                          "ready_for_packet": false
                        }
                        """);

        assertTrue(result.success());
        assertEquals(com.vinekeepers.state.planning.PlanningCanonicalNextAction.READY_FOR_PACKET, result.nextAction());
        assertTrue(result.readyForPacket());
    }

    @Test
    void parseAndValidate_depthFailureBlocksWithoutBlockingGaps() throws Exception {
        PlanningEvaluationService service = new PlanningEvaluationService(null);

        PlanningEvaluationDecision result =
                parseAndValidate(
                        service,
                        """
                        {
                          "confidence": { "score": 50, "level": "medium", "summary": "Draft thin." },
                          "gaps": [],
                          "ask_user_required": false,
                          "best_question": { "text": "", "rationale": "" },
                          "assumptions_to_add": [],
                          "issues_to_add": [],
                          "risks_to_add": [],
                          "decisions_to_add": [],
                          "ready_for_packet": false
                        }
                        """,
                        new PlanningEvaluationService.EvaluationContext("", "", "", false, "too thin", false, 0));

        assertTrue(result.success());
        assertEquals(com.vinekeepers.state.planning.PlanningCanonicalNextAction.BLOCK, result.nextAction());
        assertFalse(result.readyForPacket());
    }

    @Test
    void parseAndValidate_blocksWhenBlockingGapIsNotAskable() throws Exception {
        PlanningEvaluationService service = new PlanningEvaluationService(null);

        PlanningEvaluationDecision result =
                parseAndValidate(
                        service,
                        """
                        {
                          "confidence": { "score": 49, "level": "medium", "summary": "Blocked by one hard gap." },
                          "gaps": [
                            { "id": "gap_one", "kind": "MISSING_IMPLEMENTATION_SCOPE", "description": "Need implementation scope authority.", "blocking": true, "askable": false, "assumable": false }
                          ],
                          "ask_user_required": false,
                          "best_question": { "text": "", "rationale": "" },
                          "assumptions_to_add": [],
                          "issues_to_add": [],
                          "risks_to_add": [],
                          "decisions_to_add": [],
                          "ready_for_packet": false
                        }
                        """);

        assertTrue(result.success());
        assertEquals(com.vinekeepers.state.planning.PlanningCanonicalNextAction.BLOCK, result.nextAction());
        assertEquals("Need implementation scope authority.", result.blockReason());
    }

    private static PlanningEvaluationDecision parseAndValidate(
            PlanningEvaluationService service, String json) throws Exception {
        return parseAndValidate(
                service,
                json,
                new PlanningEvaluationService.EvaluationContext("", "", "", true, "", false, 0));
    }

    private static PlanningEvaluationDecision parseAndValidate(
            PlanningEvaluationService service,
            String json,
            PlanningEvaluationService.EvaluationContext ctx) throws Exception {
        Method parse = PlanningEvaluationService.class.getDeclaredMethod(
                "parseAndValidate",
                com.fasterxml.jackson.databind.JsonNode.class,
                FeaturePlanState.class,
                PlanningEvaluationService.EvaluationContext.class,
                boolean.class,
                boolean.class);
        parse.setAccessible(true);
        com.fasterxml.jackson.databind.JsonNode root =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        return (PlanningEvaluationDecision) parse.invoke(service, root, minimalPlan(), ctx, false, false);
    }

    private static FeaturePlanState minimalPlan() {
        return new FeaturePlanState(
                "c",
                "f",
                "s",
                "room",
                null,
                null,
                "t",
                "req",
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
                "software_feature_planning_v2",
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
