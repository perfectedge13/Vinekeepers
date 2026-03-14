package com.vinekeepers.connectors;

import com.vinekeepers.state.LifecycleContext;
import com.vinekeepers.state.LifecycleContextStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes outbound delivery by channel and lifecycle context.
 * Implements ReplySender: resolves sender from delivery target and lifecycle context
 * (configuredBotId). For lifecycle rooms, fails clearly if the resolved bot's sender is unavailable
 * (no silent fallback).
 */
public final class OutboundDeliveryRouter implements ReplySender {

    private static final Logger log = LoggerFactory.getLogger(OutboundDeliveryRouter.class);

    private final LifecycleContextStore lifecycleContextStore;
    private final Map<String, ReplySender> botIdToSender = new ConcurrentHashMap<>();
    private final Map<String, OutboundGateway> botIdToGateway = new ConcurrentHashMap<>();
    private volatile ReplySender defaultSender;
    private volatile OutboundGateway defaultGateway;

    public OutboundDeliveryRouter(LifecycleContextStore lifecycleContextStore) {
        this.lifecycleContextStore = Objects.requireNonNull(lifecycleContextStore, "lifecycleContextStore");
    }

    /**
     * Register a sender and gateway for a bot id. Used when the bot has its own connector identity.
     */
    public void registerSender(String botId, ReplySender sender, OutboundGateway gateway) {
        if (botId == null || botId.isBlank()) {
            return;
        }
        if (sender != null) {
            botIdToSender.put(botId, sender);
        }
        if (gateway != null) {
            botIdToGateway.put(botId, gateway);
        }
    }

    /**
     * Set the default sender used when the channel has no lifecycle context (e.g. main intake channel).
     */
    public void setDefaultSender(ReplySender sender) {
        this.defaultSender = sender;
    }

    /**
     * Set the default gateway (e.g. for create_channel and sink when channel has no lifecycle context).
     */
    public void setDefaultGateway(OutboundGateway gateway) {
        this.defaultGateway = gateway;
    }

    /**
     * Gateway for the given channel: lifecycle context by channelId -> configuredBotId -> that bot's gateway;
     * else default gateway. When the channel has lifecycle context and the configured bot has no gateway,
     * returns null (no fallback to default). Used by DiscordAppReplySink and CreateChannelAction.
     */
    public OutboundGateway getGatewayForChannel(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return defaultGateway;
        }
        Optional<String> botId = lifecycleContextStore.getByDeliveryTargetId(channelId)
                .map(LifecycleContext::getConfiguredBotId)
                .filter(id -> id != null && !id.isBlank());
        if (botId.isPresent()) {
            return botIdToGateway.get(botId.get());
        }
        return defaultGateway;
    }

    /**
     * Default gateway for operations that are not channel-scoped (e.g. create_channel from workflow).
     */
    public OutboundGateway getDefaultGateway() {
        return defaultGateway;
    }

    /**
     * Self user id for the given bot (from that bot's gateway getSelfUserId). Returns null if no gateway for the bot.
     */
    public String getSelfUserIdForBot(String botId) {
        if (botId == null || botId.isBlank()) {
            return null;
        }
        OutboundGateway gateway = botIdToGateway.get(botId);
        return gateway != null ? gateway.getSelfUserId() : null;
    }

    @Override
    public void send(String channelId, String messageId, String content) {
        if (channelId == null || content == null) {
            return;
        }
        Optional<LifecycleContext> contextOpt = lifecycleContextStore.getByDeliveryTargetId(channelId);
        Optional<String> configuredBotId = contextOpt
                .map(LifecycleContext::getConfiguredBotId)
                .filter(id -> id != null && !id.isBlank());

        ReplySender sender;
        if (configuredBotId.isPresent()) {
            sender = botIdToSender.get(configuredBotId.get());
            if (sender == null) {
                log.error("Lifecycle channel {} requires bot {} but no sender registered for that bot; not sending.",
                        channelId, configuredBotId.get());
                return;
            }
        } else {
            sender = defaultSender;
        }

        if (sender != null) {
            sender.send(channelId, messageId, content);
        }
    }
}
