package com.vinekeepers.workflow.actions;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningLlmPromptContractTest {

    @Test
    void expansionPrompt_bansMultiQuestionBrainstorming() throws Exception {
        String system = staticStringField(RunRequestExpansionLlmAction.class, "SYSTEM");
        assertTrue(system.contains("question_if_needed"));
        assertTrue(system.contains("recommended_action"));
        assertTrue(system.contains("ASK_USER"));
        assertTrue(system.contains("CONTINUE_SYNTHESIS"));
        assertTrue(system.contains("READY_FOR_PACKET"));
        assertTrue(system.contains("BLOCK"));
        assertTrue(system.contains("top_unresolved_gap"));
        assertTrue(system.contains("explicit_assumptions"));
        assertTrue(system.contains("repo_evidence_this_pass"));
        assertTrue(system.contains("Ground every factual claim"));
        assertTrue(system.contains("Do not fabricate file paths"));
        assertTrue(system.contains("Return exactly one JSON object"));
        assertTrue(system.contains("current_state_summary"));
        assertTrue(system.contains("never use the literal key \"fieldId\""));
        assertFalse(system.contains("candidate_open_questions"));
        assertFalse(system.contains("follow_up_decisions"));
        assertFalse(system.contains("follow_up_questions"));
        assertFalse(system.contains("\"fieldId\": \"value\""));
    }

    @Test
    void synthesisPrompt_enforcesSingleClarificationField() throws Exception {
        String system = staticStringField(RunLlmPlanningSynthesisAction.class, "SYSTEM");
        assertTrue(system.contains("question_if_needed"));
        assertTrue(system.contains("At most one clarification question"));
        assertTrue(system.contains("repo_evidence_this_pass"));
        assertTrue(system.contains("recommended_action"));
        assertTrue(system.contains("top_unresolved_gap"));
        assertTrue(system.contains("CONTINUE_SYNTHESIS"));
        assertTrue(system.contains("Return exactly one JSON object"));
        assertTrue(system.contains("Wrong: {\"artifactId\":\"requirements_spec\",\"sectionId\":\"feature_summary\""));
        assertTrue(system.contains("Right: {\"artifactId\":\"requirements_spec\",\"sectionId\":\"narrative\""));
        assertTrue(system.contains("do not name paths/packages unless observed"));
        assertTrue(system.contains("current_state_summary"));
        assertTrue(system.contains("never use the literal key \"fieldId\""));
        assertFalse(system.contains("open_questions_block"));
        assertFalse(system.contains("None — ready to implement"));
        assertFalse(system.contains("follow_up_questions"));
        assertFalse(system.contains("\"fieldId\": \"value string\""));
    }

    @Test
    void coordinatorPrompt_usesRealSectionIdsAndFieldKeys() throws Exception {
        Class<?> roleClass = Class.forName("com.vinekeepers.workflow.planning.PlanningCoordinatorRole");
        Object coordinator = Arrays.stream(roleClass.getEnumConstants())
                .filter(constant -> ((Enum<?>) constant).name().equals("COORDINATOR"))
                .findFirst()
                .orElseThrow();
        Method method = roleClass.getDeclaredMethod("systemPromptBlock");
        method.setAccessible(true);
        String system = (String) method.invoke(coordinator);
        assertTrue(system.contains("requirements_spec"));
        assertTrue(system.contains("sectionId\": \"narrative\""));
        assertTrue(system.contains("feature_summary"));
        assertTrue(system.contains("question_if_needed"));
        assertTrue(system.contains("top_unresolved_gap"));
        assertTrue(system.contains("repo_evidence_this_pass"));
        assertTrue(system.contains("literal key \"fieldId\""));
        assertTrue(system.contains("CONTINUE_SYNTHESIS"));
        assertFalse(system.contains("open_questions_block.backlog"));
        assertFalse(system.contains("\"fieldId\": \"value\""));
    }

    private static String staticStringField(Class<?> clazz, String name) throws Exception {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        return (String) f.get(null);
    }
}
