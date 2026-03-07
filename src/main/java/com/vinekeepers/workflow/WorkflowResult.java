package com.vinekeepers.workflow;

import java.util.Objects;

/**
 * Result of a workflow step or completion.
 */
public final class WorkflowResult<S> {

    private final S state;
    private final boolean done;
    private final String message;

    public WorkflowResult(S state, boolean done, String message) {
        this.state = state;
        this.done = done;
        this.message = message != null ? message : "";
    }

    public S getState() {
        return state;
    }

    public boolean isDone() {
        return done;
    }

    public String getMessage() {
        return message;
    }

    public static <S> WorkflowResult<S> continueWith(S state, String message) {
        return new WorkflowResult<>(Objects.requireNonNull(state), false, message);
    }

    public static <S> WorkflowResult<S> done(S state, String message) {
        return new WorkflowResult<>(state, true, message);
    }
}
