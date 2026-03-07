package com.vinekeepers.reasoner;

import java.util.Objects;

/**
 * Output from the reasoner (response text, tool calls, etc.).
 */
public final class ReasonerOutput {

    private final String response;
    private final boolean completed;

    public ReasonerOutput(String response, boolean completed) {
        this.response = response != null ? response : "";
        this.completed = completed;
    }

    public String getResponse() {
        return response;
    }

    public boolean isCompleted() {
        return completed;
    }

    public static ReasonerOutput of(String response) {
        return new ReasonerOutput(response, true);
    }
}
