package com.vinekeepers.bot;

import java.util.Collections;
import java.util.Set;

/**
 * Policy for which tools a bot may use (allow/deny lists).
 */
public final class ToolPolicy {

    private final Set<String> allowed;
    private final Set<String> denied;

    public ToolPolicy(Set<String> allowed, Set<String> denied) {
        this.allowed = allowed == null ? Set.of() : Set.copyOf(allowed);
        this.denied = denied == null ? Set.of() : Set.copyOf(denied);
    }

    public static ToolPolicy allowAll() {
        return new ToolPolicy(Collections.emptySet(), Collections.emptySet());
    }

    public Set<String> getAllowed() {
        return allowed;
    }

    public Set<String> getDenied() {
        return denied;
    }

    public boolean isAllowed(String toolId) {
        if (!denied.isEmpty() && denied.contains(toolId)) return false;
        if (allowed.isEmpty()) return true;
        return allowed.contains(toolId);
    }
}
