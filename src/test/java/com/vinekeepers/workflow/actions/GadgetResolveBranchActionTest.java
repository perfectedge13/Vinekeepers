package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GadgetResolveBranchActionTest {

    private final GadgetResolveBranchAction action = new GadgetResolveBranchAction();

    @Test
    void usesChoiceWhenNotOther() {
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(
                new Event("discord:g:1", "message", Map.of()),
                Map.of("branchChoice", "main"),
                Map.of());
        assertEquals("main", out.get("deployBranch"));
    }

    @Test
    void usesCustomWhenOther() {
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(
                new Event("discord:g:1", "message", Map.of()),
                Map.of("branchChoice", "other", "branchCustom", "feature/foo"),
                Map.of());
        assertEquals("feature/foo", out.get("deployBranch"));
    }

    @Test
    void otherFallsBackToMainWhenCustomBlank() {
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(
                new Event("discord:g:1", "message", Map.of()),
                Map.of("branchChoice", "OTHER", "branchCustom", "   "),
                Map.of());
        assertEquals("main", out.get("deployBranch"));
    }
}
