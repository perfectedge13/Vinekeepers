package com.vinekeepers.reasoner;

import com.vinekeepers.events.Event;

import java.util.Map;
import java.util.Objects;

/**
 * Input to the reasoner (event + context).
 */
public final class ReasonerInput {

    private final Event event;
    private final String botId;
    private final String context;
    private final Map<String, Object> currentState;
    private final String lastUserMessage;

    public ReasonerInput(Event event, String botId, String context) {
        this(event, botId, context, Map.of(), "");
    }

    public ReasonerInput(Event event, String botId, String context,
                         Map<String, Object> currentState, String lastUserMessage) {
        this.event = Objects.requireNonNull(event, "event");
        this.botId = Objects.requireNonNull(botId, "botId");
        this.context = context != null ? context : "";
        this.currentState = currentState != null ? Map.copyOf(currentState) : Map.of();
        this.lastUserMessage = lastUserMessage != null ? lastUserMessage : "";
    }

    public Event getEvent() {
        return event;
    }

    public String getBotId() {
        return botId;
    }

    public String getContext() {
        return context;
    }

    public Map<String, Object> getCurrentState() {
        return currentState;
    }

    public String getLastUserMessage() {
        return lastUserMessage;
    }
}
