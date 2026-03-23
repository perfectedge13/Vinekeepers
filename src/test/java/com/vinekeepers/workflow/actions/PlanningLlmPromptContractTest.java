package com.vinekeepers.workflow.actions;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningLlmPromptContractTest {

    @Test
    void expansionPrompt_bansMultiQuestionBrainstorming() throws Exception {
        String system = staticStringField(RunRequestExpansionLlmAction.class, "SYSTEM");
        assertTrue(system.contains("question_if_needed"));
        assertTrue(system.contains("recommended_action"));
        assertTrue(system.contains("ASK_ONE_QUESTION | ASSUME_AND_CONTINUE | POST_PACKET | BLOCK"));
        assertTrue(system.contains("top_unresolved_gap"));
        assertTrue(system.contains("explicit_assumptions"));
        assertTrue(system.contains("repo_evidence_this_pass"));
        assertTrue(system.contains("Ground every factual claim"));
        assertTrue(system.contains("Do not fabricate file paths"));
        assertFalse(system.contains("candidate_open_questions"));
    }

    @Test
    void synthesisPrompt_enforcesSingleClarificationField() throws Exception {
        String system = staticStringField(RunLlmPlanningSynthesisAction.class, "SYSTEM");
        assertTrue(system.contains("question_if_needed"));
        assertTrue(system.contains("At most one clarification question"));
        assertTrue(system.contains("repo_evidence_this_pass"));
        assertTrue(system.contains("recommended_action"));
        assertTrue(system.contains("top_unresolved_gap"));
        assertTrue(system.contains("Always leave follow_up_questions empty"));
        assertTrue(system.contains("do not name paths/packages unless observed"));
    }

    private static String staticStringField(Class<?> clazz, String name) throws Exception {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        return (String) f.get(null);
    }
}
