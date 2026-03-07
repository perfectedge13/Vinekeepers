package com.vinekeepers.tools;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of tools by id; used by ToolRunner and for policy checks.
 */
public final class ToolRegistry {

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    public void register(Tool tool) {
        tools.put(Objects.requireNonNull(tool.getId(), "tool.id"), Objects.requireNonNull(tool));
    }

    public void unregister(String toolId) {
        tools.remove(toolId);
    }

    public Tool get(String toolId) {
        return tools.get(toolId);
    }

    public Map<String, Tool> getAll() {
        return Collections.unmodifiableMap(tools);
    }
}
