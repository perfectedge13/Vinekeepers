package com.vinekeepers.workflow.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RunLlmPlanningSynthesisActionJsonTest {

    @Test
    void extractJsonObject_stripsFence() {
        String raw = "```json\n{\"upserts\":[],\"follow_up_questions\":[]}\n```";
        String out = RunLlmPlanningSynthesisAction.extractJsonObject(raw);
        assertTrue(out.contains("\"upserts\""));
        assertTrue(out.startsWith("{"));
    }

    @Test
    void extractJsonObject_embeddedInText() {
        String raw = "Here you go: {\"a\":1} thanks";
        String out = RunLlmPlanningSynthesisAction.extractJsonObject(raw);
        assertTrue(out.contains("\"a\""));
    }
}
