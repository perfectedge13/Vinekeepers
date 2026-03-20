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
                DiscordIngressModes modes = DiscordIngressModes.resolve(bot, routedBotIds);
                DiscordOwnedSpacePredicate ownedPredicate = null;
                if (modes.needsOwnedSpacePredicate()) {
                    var frs = context.getFeatureRoomStateStore();
                    var lcs = context.getLifecycleContextStore();
                    if (frs != null || lcs != null) {
                        ownedPredicate = new DiscordOwnedSpacePredicate(frs, lcs, bot.getId());
                    } else {
                        log.warn("Bot {} uses OWNED_SPACES ingress but no FeatureRoomStateStore/LifecycleContextStore in ConnectorContext; "
                                + "falling back to scoped policy without channel filter on interactions.", bot.getId());
                        modes = modes.fallbackWithoutStores();
                    }
                }
                JdaDiscordGateway gateway = new JdaDiscordGateway(token, modes, ownedPredicate);
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
     * When true, the bot's JDA gateway registers at least one inbound listener (message and/or interaction).
     */
    public static boolean discordGatewayRequiresListeners(BotDefinition bot, Set<String> routedBotIds) {
        if (bot == null) {
            return false;
        }
        return !DiscordIngressModes.resolve(bot, routedBotIds).isOutboundOnly();
    }

    /**
     * @deprecated use {@link #discordGatewayRequiresListeners(BotDefinition, Set)}
     */
    @Deprecated
    public static boolean discordInboundListenersEnabled(BotDefinition bot, Set<String> routedBotIds) {
        return discordGatewayRequiresListeners(bot, routedBotIds);
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
