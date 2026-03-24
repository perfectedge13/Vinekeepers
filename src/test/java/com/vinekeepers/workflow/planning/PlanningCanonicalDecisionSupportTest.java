package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanningCanonicalDecision;
import com.vinekeepers.state.planning.PlanningCanonicalNextAction;
import com.vinekeepers.state.planning.PlanningIntakeStage;
import com.vinekeepers.state.planning.PlanningInteractionState;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanningCanonicalDecisionSupportTest {

    @Test
    void canonicalNormalizeV1_authorizedAskOverridesAssumeGovernor() {
        FeaturePlanState plan = minimalPlan();
        PlanningMaterialRoutingOutcome material =
                new PlanningMaterialRoutingOutcome(
                        PlanningPostDraftAction.ASSUME_AND_CONTINUE,
                        PlanningPostDraftGovernor.LoopOutcome.ASSUME,
                        "",
                        false,
                        false,
                        false,
                        "READY_FOR_REVIEW",
                        false);
        ClarificationProjection ranked =
                new ClarificationProjection(
                        true, "", "[]", "{\"gapId\":\"g1\"}", 1, List.of(), false, "What is the rollout order?");
        PlanningCanonicalDecision d =
                PlanningCanonicalDecisionSupport.normalizePostDraftCanonicalV1(
                        plan, material, true, ranked, "{}", "g1", "fp");
        assertEquals(PlanningCanonicalNextAction.ASK_ONE_QUESTION, d.nextAction());
        assertEquals(PlanningIntakeStage.CLARIFYING, d.stage());
        assertEquals("What is the rollout order?", d.questionText());
    }

    @Test
    void canonicalNormalizeV1_governorBlockWinsOverAuthorizedGap() {
        FeaturePlanState plan = minimalPlan();
        PlanningMaterialRoutingOutcome material =
                new PlanningMaterialRoutingOutcome(
                        PlanningPostDraftAction.BLOCK,
                        PlanningPostDraftGovernor.LoopOutcome.BLOCK,
                        "**Planning paused**",
                        false,
                        false,
                        false,
                        "FAILED",
                        false);
        ClarificationProjection ranked =
                new ClarificationProjection(
                        true, "", "[]", "{\"gapId\":\"g1\"}", 1, List.of(), false, "Ignored when blocked");
        PlanningCanonicalDecision d =
                PlanningCanonicalDecisionSupport.normalizePostDraftCanonicalV1(
                        plan, material, true, ranked, "{}", "g1", "fp");
        assertEquals(PlanningCanonicalNextAction.BLOCK, d.nextAction());
        assertEquals(PlanningIntakeStage.FAILED, d.stage());
    }

    @Test
    void projectToSpread_clearsClarificationCarrierWhenNotAsk() {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("planningClarificationQuestionText", "ghost");
        spread.put("planningClarificationChoicesJson", "[{\"id\":\"x\"}]");
        spread.put("planningClarificationMetaJson", "{\"gapId\":\"z\"}");
        spread.put("planningClarificationOrchestratorPrompt", "prompt");
        spread.put("planningClarificationUseStructuredChoices", "true");
        PlanningCanonicalDecision post =
                PlanningCanonicalDecision.create(
                        "post_draft",
                        PlanningIntakeStage.DRAFTING,
                        PlanningCanonicalNextAction.POST_PACKET,
                        PlanningInteractionState.NONE,
                        "MATERIALIZED",
                        "",
                        true,
                        false,
                        false,
                        "",
                        "",
                        "should not appear",
                        "",
                        List.of(),
                        "fp");
        PlanningCanonicalDecisionSupport.projectToSpread(spread, post);
        assertEquals("", spread.get("planningClarificationQuestionText"));
        assertEquals("[]", spread.get("planningClarificationChoicesJson"));
        assertEquals("{}", spread.get("planningClarificationMetaJson"));
        assertEquals("", spread.get("planningClarificationOrchestratorPrompt"));
        assertEquals("false", spread.get("planningClarificationUseStructuredChoices"));
    }

    @Test
    void projectToSpread_keepsQuestionWhenAsk() {
        Map<String, Object> spread = new LinkedHashMap<>();
        PlanningCanonicalDecision ask =
                PlanningCanonicalDecision.create(
                        "post_draft",
                        PlanningIntakeStage.CLARIFYING,
                        PlanningCanonicalNextAction.ASK_ONE_QUESTION,
                        PlanningInteractionState.WAITING_FOR_TEXT_REPLY,
                        "MATERIALIZED",
                        "",
                        false,
                        false,
                        false,
                        "",
                        "g1",
                        "Only when authorized",
                        "",
                        List.of(),
                        "fp");
        PlanningCanonicalDecisionSupport.projectToSpread(spread, ask);
        assertEquals("Only when authorized", spread.get("planningClarificationQuestionText"));
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
