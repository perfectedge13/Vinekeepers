package com.vinekeepers.workflow;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Definition of a configurable workflow: id and list of step configs (type, prompt, storeIn, action, bind, etc.).
 */
public final class WorkflowDefinition {

    private final String id;
    private final String defaultModel;
    private final List<Map<String, Object>> steps;

    public WorkflowDefinition(String id, List<Map<String, Object>> steps) {
        this(id, null, steps);
    }

    public WorkflowDefinition(String id, String defaultModel, List<Map<String, Object>> steps) {
        this.id = id != null ? id : "";
        this.defaultModel = defaultModel != null && !defaultModel.isBlank() ? defaultModel : null;
        this.steps = steps != null ? List.copyOf(steps) : List.of();
    }

    public String getId() {
        return id;
    }

    public List<Map<String, Object>> getSteps() {
        return steps;
    }

    public String getDefaultModel() {
        return defaultModel;
    }
}
