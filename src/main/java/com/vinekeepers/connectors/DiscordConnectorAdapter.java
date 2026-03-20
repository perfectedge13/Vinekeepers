package com.vinekeepers.connectors;

import com.vinekeepers.bot.BotDefinition;
import com.vinekeepers.bot.ConnectorIdentity;
import com.vinekeepers.env.Env;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Discord connector adapter: per-bot gateway/sender/default registration and event source startup only.
 * Engine, sink, and action registration remain in Bootstrap.
 */
public final class DiscordConnectorAdapter implements ConnectorAdapter {

    private static final Logger log = LoggerFactory.getLogger(DiscordConnectorAdapter.class);

    private final DiscordConnectorConfig config;
    private final Set<String> routedBotIds;
    private final List<DiscordEventSource> discordSources = new ArrayList<>();

    public DiscordConnectorAdapter(DiscordConnectorConfig config, Set<String> routedBotIds) {
        this.config = Objects.requireNonNull(config, "config");
        this.routedBotIds = routedBotIds != null ? Set.copyOf(routedBotIds) : Set.of();
    }

    @Override
    public void registerBots(List<BotDefinition> bots, ConnectorContext context) {
        if (bots == null || context == null) {
            return;
        }
        var router = context.getOutboundDeliveryRouter();
        var eventBus = context.getEventBus();

        boolean anyWithToken = bots.stream()
                .anyMatch(b -> tokenEnvKey(b) != null && !tokenEnvKey(b).isBlank());
        if (anyWithToken) {
            for (BotDefinition bot : bots) {
                String envKey = tokenEnvKey(bot);
                if (envKey == null || envKey.isBlank()) {
                    continue;
                }
                String token = Env.get(envKey, "");
                if (token.isBlank()) {
                    log.warn("Bot {} has discord tokenEnvKey {} but token is blank; skipping Discord connector for this bot.", bot.getId(), envKey);
                    continue;
                }
                boolean outboundOnly = !discordInboundListenersEnabled(bot, routedBotIds);
                JdaDiscordGateway gateway = new JdaDiscordGateway(token, outboundOnly);
                DiscordEventSource source = new DiscordEventSource(gateway);
                router.registerSender(bot.getId(), source, gateway);
                if (router.getDefaultGateway() == null) {
                    router.setDefaultSender(source);
                    router.setDefaultGateway(gateway);
                }
                source.start(eventBus);
                discordSources.add(source);
            }
        }
        if (router.getDefaultGateway() == null && config.getDefaultTokenEnvKey() != null && !config.getDefaultTokenEnvKey().isBlank()) {
            String token = Env.get(config.getDefaultTokenEnvKey(), "");
            if (!token.isBlank()) {
                JdaDiscordGateway gateway = new JdaDiscordGateway(token);
                DiscordEventSource source = new DiscordEventSource(gateway);
                router.setDefaultSender(source);
                router.setDefaultGateway(gateway);
                for (BotDefinition bot : bots) {
                    router.registerSender(bot.getId(), source, gateway);
                }
                source.start(eventBus);
                discordSources.add(source);
            }
        }
    }

    /**
     * When true, the bot's JDA gateway registers message and interaction listeners (inbound).
     * Routed bots and Discord lifecycle space owners ({@code handlesOwnedSpaces}) must be inbound so
     * component interactions on that bot's messages are handled.
     */
    public static boolean discordInboundListenersEnabled(BotDefinition bot, Set<String> routedBotIds) {
        if (bot == null) {
            return false;
        }
        Set<String> routed = routedBotIds != null ? routedBotIds : Set.of();
        if (routed.contains(bot.getId())) {
            return true;
        }
        return bot.getConnectorIdentity("discord")
                .map(d -> Boolean.TRUE.equals(d.getBoolean("handlesOwnedSpaces")))
                .orElse(false);
    }

    private static String tokenEnvKey(BotDefinition bot) {
        return bot.getConnectorIdentity("discord")
                .map(d -> d.getString("tokenEnvKey"))
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);
    }

    /**
     * Returns the list of Discord event sources created by this adapter (for shutdown).
     */
    public List<DiscordEventSource> getDiscordSources() {
        return List.copyOf(discordSources);
    }
}
