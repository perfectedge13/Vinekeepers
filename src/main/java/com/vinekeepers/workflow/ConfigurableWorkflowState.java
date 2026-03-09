package com.vinekeepers.workflow;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable workflow state: flexible map plus current step index for config-driven workflows.
 */
public final class ConfigurableWorkflowState {

    public enum Status {
        ACTIVE,
        WAITING_INPUT,
        COMPLETED,
        ERROR
    }

    private final Map<String, Object> data;
    private int stepIndex;
    private Status status;
    private String waitingForField;
    private String pendingPrompt;
    private long updatedAt;

    public ConfigurableWorkflowState() {
        this.data = new ConcurrentHashMap<>();
        this.stepIndex = 0;
        this.status = Status.ACTIVE;
        this.updatedAt = System.currentTimeMillis();
    }

    public ConfigurableWorkflowState(int initialStepIndex) {
        this.data = new ConcurrentHashMap<>();
        this.stepIndex = initialStepIndex;
        this.status = Status.ACTIVE;
        this.updatedAt = System.currentTimeMillis();
    }

    public Map<String, Object> getData() {
        return data;
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public void setStepIndex(int stepIndex) {
        this.stepIndex = stepIndex;
        touch();
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status != null ? status : Status.ACTIVE;
        touch();
    }

    public String getWaitingForField() {
        return waitingForField;
    }

    public void setWaitingForField(String waitingForField) {
        this.waitingForField = waitingForField;
        touch();
    }

    public String getPendingPrompt() {
        return pendingPrompt;
    }

    public void setPendingPrompt(String pendingPrompt) {
        this.pendingPrompt = pendingPrompt;
        touch();
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    public void markActive() {
        this.status = Status.ACTIVE;
        this.waitingForField = null;
        this.pendingPrompt = null;
        touch();
    }

    public void markWaiting(String field, String prompt) {
        this.status = Status.WAITING_INPUT;
        this.waitingForField = field;
        this.pendingPrompt = prompt;
        touch();
    }

    public void markCompleted() {
        this.status = Status.COMPLETED;
        this.waitingForField = null;
        this.pendingPrompt = null;
        touch();
    }

    public void markError() {
        this.status = Status.ERROR;
        touch();
    }

    public void resetForNewRun() {
        data.clear();
        this.stepIndex = 0;
        this.status = Status.ACTIVE;
        this.waitingForField = null;
        this.pendingPrompt = null;
        touch();
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
        if (key != null) {
            data.put(key, value);
            touch();
        }
    }

    public boolean has(String key) {
        return data.containsKey(key);
    }

    /**
     * Remove the given keys from state data (e.g. for edit-reprompt so user re-enters).
     * Does not remove __sessionKey.
     */
    public void clearKeys(Collection<String> keys) {
        if (keys == null) return;
        for (String key : keys) {
            if (key != null && !"__sessionKey".equals(key)) {
                data.remove(key);
            }
        }
        touch();
    }
}
