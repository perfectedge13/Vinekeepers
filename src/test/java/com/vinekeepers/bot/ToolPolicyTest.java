package com.vinekeepers.bot;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolPolicyTest {

    @Test
    void allowAllPermitsAnyTool() {
        ToolPolicy policy = ToolPolicy.allowAll();
        assertTrue(policy.isAllowed("any-tool"));
        assertTrue(policy.getAllowed().isEmpty());
        assertTrue(policy.getDenied().isEmpty());
    }

    @Test
    void deniedTakesPrecedence() {
        ToolPolicy policy = new ToolPolicy(Set.of("a", "b"), Set.of("b"));
        assertTrue(policy.isAllowed("a"));
        assertFalse(policy.isAllowed("b"));
    }

    @Test
    void allowedOnlyRestrictsToSet() {
        ToolPolicy policy = new ToolPolicy(Set.of("a", "b"), Set.of());
        assertTrue(policy.isAllowed("a"));
        assertTrue(policy.isAllowed("b"));
        assertFalse(policy.isAllowed("c"));
    }

    @Test
    void nullAllowedAndDeniedTreatedAsEmpty() {
        ToolPolicy policy = new ToolPolicy(null, null);
        assertTrue(policy.isAllowed("x"));
    }
}
