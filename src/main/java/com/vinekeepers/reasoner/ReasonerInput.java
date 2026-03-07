package com.vinekeepers.reasoner;

import com.vinekeepers.events.Event;

import java.util.Objects;

/**
 * Input to the reasoner (event + context).
 */
public final class ReasonerInput {

    private final Event event;
    private final String botId;
    private final String context;

    public ReasonerInput(Event event, String botId, String context) {
        this.event = Objects.requireNonNull(event, "event");
        this.botId = Objects.requireNonNull(botId, "botId");
        this.context = context != null ? context : "";
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
}
