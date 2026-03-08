package com.vinekeepers.tools;

import com.vinekeepers.bot.ToolPolicy;

import java.util.Map;
import java.util.Objects;

/**
 * Runs tools subject to a ToolPolicy (allow/deny).
 */
public final class ToolRunner {

    private final ToolRegistry registry;

    public ToolRunner(ToolRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public boolean hasTool(String toolId) {
        return registry.get(toolId) != null;
    }

    /**
     * Run the tool by id with args, if allowed by policy. Returns result or throws.
     */
    public Object run(String toolId, Map<String, Object> args, ToolPolicy policy) {
        if (policy != null && !policy.isAllowed(toolId)) {
            throw new SecurityException("Tool not allowed: " + toolId);
        }
        Tool tool = registry.get(toolId);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolId);
        }
        return tool.run(args != null ? args : Map.of());
    }
}
