package com.vinekeepers.workflow;

import com.vinekeepers.interactions.OutboundResponse;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of executing a single workflow step.
 */
public final class StepResult {

    private final Integer nextStepIndex;
    private final String storeIn;
    private final Object storeValue;
    private final StepOutcome outcome;
    private final String message;
    private final String promptMessage;
    private final String waitingForField;
    private final OutboundResponse richReply;

    public StepResult(Integer nextStepIndex, String storeIn, Object storeValue, boolean done, String message) {
        this(nextStepIndex, storeIn, storeValue, done ? StepOutcome.COMPLETE : StepOutcome.CONTINUE,
                message, "", null, null);
    }

    public StepResult(Integer nextStepIndex, String storeIn, Object storeValue, StepOutcome outcome,
                      String message, String promptMessage, String waitingForField) {
        this(nextStepIndex, storeIn, storeValue, outcome, message, promptMessage, waitingForField, null);
    }

    public StepResult(Integer nextStepIndex, String storeIn, Object storeValue, StepOutcome outcome,
                      String message, String promptMessage, String waitingForField, OutboundResponse richReply) {
        this.nextStepIndex = nextStepIndex;
        this.storeIn = storeIn;
        this.storeValue = storeValue;
        this.outcome = outcome != null ? outcome : StepOutcome.CONTINUE;
        this.message = message != null ? message : "";
        this.promptMessage = promptMessage != null ? promptMessage : "";
        this.waitingForField = waitingForField;
        this.richReply = richReply;
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

    public StepOutcome getOutcome() {
        return outcome;
    }

    public boolean isDone() {
        return outcome == StepOutcome.COMPLETE;
    }

    public String getMessage() {
        return message;
    }

    public String getPromptMessage() {
        return promptMessage;
    }

    public String getWaitingForField() {
        return waitingForField;
    }

    public Optional<OutboundResponse> getRichReply() {
        return Optional.ofNullable(richReply);
    }

    public static StepResult advance(String storeIn, Object storeValue) {
        return new StepResult(null, storeIn, storeValue, StepOutcome.CONTINUE, "", "", null);
    }

    public static StepResult goTo(int nextStepIndex) {
        return new StepResult(nextStepIndex, null, null, StepOutcome.CONTINUE, "", "", null);
    }

    public static StepResult goTo(int nextStepIndex, String storeIn, Object storeValue) {
        return new StepResult(nextStepIndex, storeIn, storeValue, StepOutcome.CONTINUE, "", "", null);
    }

    public static StepResult done(String message) {
        return new StepResult(null, null, null, StepOutcome.COMPLETE, Objects.requireNonNull(message), "", null);
    }

    public static StepResult waiting(String promptMessage, String waitingForField) {
        return new StepResult(null, null, null, StepOutcome.WAITING, "",
                Objects.requireNonNull(promptMessage), waitingForField, null);
    }

    public static StepResult waiting(String promptMessage, String waitingForField, OutboundResponse richReply) {
        return new StepResult(null, null, null, StepOutcome.WAITING, "",
                Objects.requireNonNull(promptMessage), waitingForField, richReply);
    }

    public static StepResult error(String message) {
        return new StepResult(null, null, null, StepOutcome.ERROR, Objects.requireNonNull(message), "", null);
    }
}
