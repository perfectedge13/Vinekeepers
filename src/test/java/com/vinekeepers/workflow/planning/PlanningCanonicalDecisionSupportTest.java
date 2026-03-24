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
import static org.junit.jupiter.api.Assertions.assertFalse;

class PlanningCanonicalDecisionSupportTest {

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
                        PlanningCanonicalNextAction.READY_FOR_PACKET,
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
                        PlanningCanonicalNextAction.ASK_USER,
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

    @Test
    void supportNoLongerDerivesCanonicalDecisionFromEvaluation() {
        boolean present = java.util.Arrays.stream(PlanningCanonicalDecisionSupport.class.getDeclaredMethods())
                .anyMatch(method -> "normalizeFromEvaluation".equals(method.getName()));
        assertFalse(present);
    }
}
