package com.vinekeepers.workflow;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable workflow state: flexible map plus current step index for config-driven workflows.
 */
public final class ConfigurableWorkflowState {

    private final Map<String, Object> data;
    private int stepIndex;

    public ConfigurableWorkflowState() {
        this.data = new ConcurrentHashMap<>();
        this.stepIndex = 0;
    }

    public ConfigurableWorkflowState(int initialStepIndex) {
        this.data = new ConcurrentHashMap<>();
        this.stepIndex = initialStepIndex;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public void setStepIndex(int stepIndex) {
        this.stepIndex = stepIndex;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object v = data.get(key);
        if (v == null) return null;
        if (type.isInstance(v)) return (T) v;
        return null;
    }

    public Object get(String key) {
        return data.get(key);
    }

    public void put(String key, Object value) {
        if (key != null) data.put(key, value);
    }

    public boolean has(String key) {
        return data.containsKey(key);
    }
}
