package com.vinekeepers.workflow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Definition of a configurable workflow: id, optional workflow-level {@code llm} defaults from YAML, and steps.
 */
public final class WorkflowDefinition {

    private final String id;
    private final List<Map<String, Object>> steps;
    /** Keys typically {@code provider}, {@code model}, {@code timeoutMs} — merged into state as {@code workflowLlm*}. */
    private final Map<String, Object> llm;

    public WorkflowDefinition(String id, List<Map<String, Object>> steps, Map<String, Object> llm) {
        this.id = id != null ? id : "";
        this.steps = steps != null ? List.copyOf(steps) : List.of();
        this.llm = llm != null && !llm.isEmpty() ? Map.copyOf(llm) : Map.of();
    }

    public WorkflowDefinition(String id, List<Map<String, Object>> steps) {
        this(id, steps, Map.of());
    }

    public String getId() {
        return id;
    }

    public List<Map<String, Object>> getSteps() {
        return steps;
    }

    public Map<String, Object> getLlm() {
        return llm;
    }

    /**
     * Normalize YAML {@code llm} map (any nested map) to string-keyed map for {@link #WorkflowDefinition}.
     */
    public static Map<String, Object> copyLlmMap(Object llmYaml) {
        if (!(llmYaml instanceof Map<?, ?> raw) || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            if (e.getKey() != null) {
                out.put(e.getKey().toString(), e.getValue());
            }
        }
        return out.isEmpty() ? Map.of() : Map.copyOf(out);
    }
}
