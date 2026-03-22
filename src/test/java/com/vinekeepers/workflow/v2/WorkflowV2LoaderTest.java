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

        WorkflowV2Model m = WorkflowV2Loader.load("id", wf);
        assertEquals("a", m.getEntryPhase());
        assertTrue(m.getPhases().containsKey("a"));
        assertEquals(1, m.getPhases().get("a").getPipeline().size());
        assertTrue(m.getCapabilities().containsKey("cap1"));
    }
}
