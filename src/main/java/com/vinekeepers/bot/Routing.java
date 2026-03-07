package com.vinekeepers.bot;

import java.util.Objects;

/**
 * A routing rule: filter + target bot id.
 */
public final class Routing {

    private final RoutingFilter filter;
    private final String botId;

    public Routing(RoutingFilter filter, String botId) {
        this.filter = Objects.requireNonNull(filter, "filter");
        this.botId = Objects.requireNonNull(botId, "botId");
    }

    public RoutingFilter getFilter() {
        return filter;
    }

    public String getBotId() {
        return botId;
    }
}
