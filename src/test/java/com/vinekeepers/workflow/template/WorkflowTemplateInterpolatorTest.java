package com.vinekeepers.workflow.template;

import com.vinekeepers.workflow.ConfigurableWorkflowState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowTemplateInterpolatorTest {

    @Test
    void safeModeSubstitutesOnlyAllowlistedKeys() {
        Map<String, Object> m = new HashMap<>();
        m.put("nextQuestionPlain", "Hello");
        m.put("planningPhase", "SECRET");
        WorkflowTemplatePolicy pol = WorkflowTemplatePolicy.fromYaml(Map.of("exposeInternal", false));
        String out = WorkflowTemplateInterpolator.interpolate("Q: {{nextQuestionPlain}} phase={{planningPhase}}", m, pol);
        assertTrue(out.contains("Hello"));
        assertTrue(out.contains("{{planningPhase}}"));
    }

    @Test
    void legacyModeSubstitutesAnyKeyFromState() {
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        state.put("planningPhase", "DRAFTING");
        String out =
                WorkflowTemplateInterpolator.interpolate(
                        "p={{planningPhase}}", state, WorkflowTemplatePolicy.LEGACY_FULL_STATE);
        assertEquals("p=DRAFTING", out);
    }

    @Test
    void customAllowedKeysFromYaml() {
        Map<String, Object> m = Map.of("customKey", "X");
        WorkflowTemplatePolicy pol =
                WorkflowTemplatePolicy.fromYaml(
                        Map.of("exposeInternal", false, "allowedKeys", List.of("customKey")));
        String out = WorkflowTemplateInterpolator.interpolate("{{customKey}}", m, pol);
        assertEquals("X", out);
    }
}
