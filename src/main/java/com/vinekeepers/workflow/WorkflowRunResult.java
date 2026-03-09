package com.vinekeepers.workflow;

import com.vinekeepers.interactions.OutboundResponse;

import java.util.Optional;

/**
 * Outcome of running a workflow for a single event.
 */
public final class WorkflowRunResult {

    private final String replyMessage;
    private final Optional<OutboundResponse> richReply;
    private final boolean waiting;
    private final boolean completed;
    private final String waitingForField;
    private final String errorMessage;

    public WorkflowRunResult(String replyMessage, boolean waiting, boolean completed,
                             String waitingForField, String errorMessage) {
        this(replyMessage, null, waiting, completed, waitingForField, errorMessage);
    }

    public WorkflowRunResult(String replyMessage, OutboundResponse richReply, boolean waiting, boolean completed,
                             String waitingForField, String errorMessage) {
        this.replyMessage = replyMessage != null ? replyMessage : "";
        this.richReply = Optional.ofNullable(richReply);
        this.waiting = waiting;
        this.completed = completed;
        this.waitingForField = waitingForField;
        this.errorMessage = errorMessage;
    }

    public String getReplyMessage() {
        return replyMessage;
    }

    public Optional<OutboundResponse> getRichReply() {
        return richReply;
    }

    public boolean isWaiting() {
        return waiting;
    }

    public boolean isCompleted() {
        return completed;
    }

    public String getWaitingForField() {
        return waitingForField;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public static WorkflowRunResult continueWithoutReply() {
        return new WorkflowRunResult("", false, false, null, null);
    }

    public static WorkflowRunResult waiting(String replyMessage, String waitingForField) {
        return new WorkflowRunResult(replyMessage, null, true, false, waitingForField, null);
    }

    public static WorkflowRunResult waiting(OutboundResponse richReply, String waitingForField) {
        String msg = richReply != null && richReply.getText().isPresent() ? richReply.getText().get() : "";
        return new WorkflowRunResult(msg, richReply, true, false, waitingForField, null);
    }

    public static WorkflowRunResult completed(String replyMessage) {
        return new WorkflowRunResult(replyMessage, null, false, true, null, null);
    }

    public static WorkflowRunResult completed(OutboundResponse richReply) {
        String msg = richReply != null && richReply.getText().isPresent() ? richReply.getText().get() : "";
        return new WorkflowRunResult(msg, richReply, false, true, null, null);
    }

    public static WorkflowRunResult error(String errorMessage) {
        return new WorkflowRunResult("", false, false, null, errorMessage);
    }
}
