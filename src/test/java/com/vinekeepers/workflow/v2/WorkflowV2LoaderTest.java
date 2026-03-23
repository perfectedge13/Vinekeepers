package com.vinekeepers.workflow.v2;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowV2LoaderTest {

    @Test
    void loadsPhasesAndCapabilities() {
        Map<String, Object> wf = new LinkedHashMap<>();
        wf.put("entryPhase", "a");
        Map<String, Object> phases = new LinkedHashMap<>();
        phases.put("a", Map.of("pipeline", List.of("cap1")));
        wf.put("phases", phases);
        wf.put("capabilities", Map.of("cap1", Map.of("kind", "legacy_action", "action", "v2_noop")));
        wf.put(
                "deliberation",
                Map.of("profileHint", "test_profile", "label", "unit_deliberation"));

        WorkflowV2Model m = WorkflowV2Loader.load("id", wf);
        assertEquals("a", m.getEntryPhase());
        assertTrue(m.getPhases().containsKey("a"));
        assertEquals(1, m.getPhases().get("a").getPipeline().size());
        assertTrue(m.getCapabilities().containsKey("cap1"));
        assertEquals("test_profile", m.getDeliberation().get("profileHint"));
        assertEquals("unit_deliberation", m.getDeliberation().get("label"));
    }

    @Test
    void loadsConfigurableStepsInlineSteps() {
        Map<String, Object> wf = new LinkedHashMap<>();
        wf.put("entryPhase", "a");
        wf.put("phases", Map.of("a", Map.of("pipeline", List.of("emb"))));
        wf.put(
                "capabilities",
                Map.of(
                        "emb",
                        Map.of(
                                "kind",
                                "configurable_steps",
                                "steps",
                                List.of(Map.of("type", "done", "message", "ok")))));

        WorkflowV2Model m = WorkflowV2Loader.load("w", wf);
        assertEquals(1, m.getCapabilities().get("emb").getSteps().size());
        assertEquals("done", m.getCapabilities().get("emb").getSteps().get(0).get("type"));
    }
}
