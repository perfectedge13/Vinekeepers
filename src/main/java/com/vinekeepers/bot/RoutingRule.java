package com.vinekeepers.bot;

import java.util.Objects;

/**
 * One rule in the routing policy: filter + target bot id.
 */
public final class RoutingRule {

    private final RoutingFilter filter;
    private final String botId;

    public RoutingRule(RoutingFilter filter, String botId) {
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
