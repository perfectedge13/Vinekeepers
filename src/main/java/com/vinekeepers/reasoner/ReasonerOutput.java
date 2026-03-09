package com.vinekeepers.reasoner;

import com.vinekeepers.interactions.OutboundResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Output from the reasoner (reply text, state patch, proposed tool calls, optional rich reply).
 */
public final class ReasonerOutput {

    private final String replyText;
    private final OutboundResponse richReply;
    private final boolean completed;
    private final Map<String, Object> statePatch;
    private final List<ProposedToolCall> proposedToolCalls;

    public ReasonerOutput(String replyText, boolean completed,
                          Map<String, Object> statePatch, List<ProposedToolCall> proposedToolCalls) {
        this(replyText, null, completed, statePatch, proposedToolCalls);
    }

    public ReasonerOutput(String replyText, OutboundResponse richReply, boolean completed,
                          Map<String, Object> statePatch, List<ProposedToolCall> proposedToolCalls) {
        this.replyText = replyText != null ? replyText : "";
        this.richReply = richReply;
        this.completed = completed;
        this.statePatch = statePatch != null ? Map.copyOf(statePatch) : Map.of();
        this.proposedToolCalls = proposedToolCalls != null ? List.copyOf(proposedToolCalls) : List.of();
    }

    public String getReplyText() {
        return replyText;
    }

    public String getResponse() {
        return replyText;
    }

    public boolean isCompleted() {
        return completed;
    }

    public Map<String, Object> getStatePatch() {
        return statePatch;
    }

    public List<ProposedToolCall> getProposedToolCalls() {
        return proposedToolCalls;
    }

    public Optional<OutboundResponse> getRichReply() {
        return Optional.ofNullable(richReply);
    }

    public static ReasonerOutput empty() {
        return new ReasonerOutput("", false, Map.of(), List.of());
    }

    public static ReasonerOutput of(String response) {
        return new ReasonerOutput(response, true, Map.of(), List.of());
    }
}
