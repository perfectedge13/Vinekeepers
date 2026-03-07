package com.vinekeepers.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowDefinitionTest {

    @Test
    void getIdAndGetSteps() {
        List<Map<String, Object>> steps = List.of(Map.of("type", "done", "message", "OK"));
        WorkflowDefinition def = new WorkflowDefinition("my-flow", steps);
        assertEquals("my-flow", def.getId());
        assertEquals(1, def.getSteps().size());
        assertEquals("done", def.getSteps().get(0).get("type"));
    }

    @Test
    void nullIdBecomesEmpty() {
        WorkflowDefinition def = new WorkflowDefinition(null, List.of());
        assertEquals("", def.getId());
    }

    @Test
    void nullStepsBecomesEmptyList() {
        WorkflowDefinition def = new WorkflowDefinition("x", null);
        assertTrue(def.getSteps().isEmpty());
    }

    @Test
    void stepsIsCopy() {
        List<Map<String, Object>> steps = new java.util.ArrayList<>(List.of(Map.of("type", "done")));
        WorkflowDefinition def = new WorkflowDefinition("id", steps);
        steps.clear();
        assertEquals(1, def.getSteps().size());
    }
}
