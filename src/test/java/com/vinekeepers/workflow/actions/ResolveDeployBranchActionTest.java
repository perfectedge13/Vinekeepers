package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResolveDeployBranchActionTest {

    private final ResolveDeployBranchAction action = new ResolveDeployBranchAction();

    @Test
    void usesBranchChoiceWhenNotOther() {
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(
                new Event("s", "k", Map.of()),
                Map.of("branchChoice", "develop"),
                Map.of());
        assertEquals("develop", out.get("deployBranch"));
    }

    @Test
    void usesBranchCustomWhenOther() {
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) action.run(
                new Event("s", "k", Map.of()),
                Map.of("branchChoice", "other", "branchCustom", "feature/x"),
                Map.of());
        assertEquals("feature/x", out.get("deployBranch"));
    }
}
