package com.vinekeepers.workflow;

import com.vinekeepers.bot.NormalizedEventContext;
import com.vinekeepers.events.Event;

/**
 * Factory and defaults for runtime session key strategies.
 */
public final class SessionKeyStrategies {

    private SessionKeyStrategies() {
    }

    public static SessionKeyStrategy resolve(String configuredStrategy, Event event) {
        String name = configuredStrategy;
        if (name == null || name.isBlank()) {
            name = defaultStrategyName(event);
        }
        return switch (name.toLowerCase()) {
            case "channel" -> SessionKeyStrategies::channelOnly;
            case "thread" -> SessionKeyStrategies::threadAware;
            case "channel_user" -> SessionKeyStrategies::channelAndUser;
            default -> SessionKeyStrategies::channelAndUser;
        };
    }

    private static String defaultStrategyName(Event event) {
        NormalizedEventContext context = NormalizedEventContext.from(event);
        if ("discord".equals(context.getSourceType())) {
            return "channel_user";
        }
        return "channel";
    }

    private static String channelOnly(String botId, Event event) {
        NormalizedEventContext context = NormalizedEventContext.from(event);
        if (context.getChannelId() != null && !context.getChannelId().isBlank()) {
            return "bot:" + botId + ":conv:" + context.getChannelId();
        }
        return "bot:" + botId + ":state";
    }

    private static String channelAndUser(String botId, Event event) {
        NormalizedEventContext context = NormalizedEventContext.from(event);
        if (context.getChannelId() != null && !context.getChannelId().isBlank()
                && context.getActorId() != null && !context.getActorId().isBlank()) {
            return "bot:" + botId + ":conv:" + context.getChannelId() + ":" + context.getActorId();
        }
        return channelOnly(botId, event);
    }

    private static String threadAware(String botId, Event event) {
        NormalizedEventContext context = NormalizedEventContext.from(event);
        if (context.getThreadId() != null && !context.getThreadId().isBlank()) {
            return "bot:" + botId + ":conv:" + context.getThreadId();
        }
        return channelAndUser(botId, event);
    }
}
