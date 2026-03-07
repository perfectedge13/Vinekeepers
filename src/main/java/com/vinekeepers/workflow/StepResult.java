package com.vinekeepers.workflow;

import java.util.Objects;

/**
 * Result of executing a single workflow step: next step index, optional store, done flag, message.
 */
public final class StepResult {

    private final Integer nextStepIndex;
    private final String storeIn;
    private final Object storeValue;
    private final boolean done;
    private final String message;

    public StepResult(Integer nextStepIndex, String storeIn, Object storeValue, boolean done, String message) {
        this.nextStepIndex = nextStepIndex;
        this.storeIn = storeIn;
        this.storeValue = storeValue;
        this.done = done;
        this.message = message != null ? message : "";
    }

    public Integer getNextStepIndex() {
        return nextStepIndex;
    }

    public String getStoreIn() {
        return storeIn;
    }

    public Object getStoreValue() {
        return storeValue;
    }

    public boolean isDone() {
        return done;
    }

    public String getMessage() {
        return message;
    }

    public static StepResult advance(String storeIn, Object storeValue) {
        return new StepResult(null, storeIn, storeValue, false, "");
    }

    public static StepResult goTo(int nextStepIndex) {
        return new StepResult(nextStepIndex, null, null, false, "");
    }

    public static StepResult goTo(int nextStepIndex, String storeIn, Object storeValue) {
        return new StepResult(nextStepIndex, storeIn, storeValue, false, "");
    }

    public static StepResult done(String message) {
        return new StepResult(null, null, null, true, Objects.requireNonNull(message));
    }
}
