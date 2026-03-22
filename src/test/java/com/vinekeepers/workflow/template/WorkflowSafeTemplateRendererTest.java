package com.vinekeepers.workflow.template;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowSafeTemplateRendererTest {

    @Test
    void onlyAllowlistedKeysReplaced() {
        String out =
                WorkflowSafeTemplateRenderer.render(
                        "Hello {{name}} {{secret}}",
                        Set.of("name"),
                        Map.of("name", "Ada", "secret", "X"));
        assertEquals("Hello Ada {{secret}}", out);
    }
}
