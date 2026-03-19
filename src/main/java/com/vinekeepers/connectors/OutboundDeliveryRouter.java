package com.vinekeepers.connectors;

import com.vinekeepers.state.LifecycleContext;
import com.vinekeepers.state.LifecycleContextStore;
import com.vinekeepers.state.planning.FeatureRoomState;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.planning.PlanningRole;
import com.vinekeepers.state.planning.RoomParticipant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * (1) Routes outbound delivery (ReplySender) by target and lifecycle context: resolves which
 * sender to use for a given channel/thread from LifecycleContextStore and configuredBotId;
 * implements ReplySender and delegates to the resolved sender; for lifecycle channels does not
 * silently fall back when the configured bot has no sender.
 * (2) Holds and resolves gateways by bot/channel for connector-owned code (DiscordSpaceOperations,
 * DiscordAppReplySink). The gateway is the connector execution surface for channel/thread/permission
 * and send operations; the router performs routing and gateway resolution, not delivery execution
 * beyond delegating send to the resolved ReplySender.
 */
public final class OutboundDeliveryRouter implements ReplySender {

    private static final Logger log = LoggerFactory.getLogger(OutboundDeliveryRouter.class);

    private final LifecycleContextStore lifecycleContextStore;
    private final FeatureRoomStateStore featureRoomStateStore;
    private final Map<String, ReplySender> botIdToSender = new ConcurrentHashMap<>();
    private final Map<String, OutboundGateway> botIdToGateway = new ConcurrentHashMap<>();
    private volatile ReplySender defaultSender;
    private volatile OutboundGateway defaultGateway;

    public OutboundDeliveryRouter(LifecycleContextStore lifecycleContextStore) {
        this.lifecycleContextStore = Objects.requireNonNull(lifecycleContextStore, "lifecycleContextStore");
        this.featureRoomStateStore = null;
    }

    public OutboundDeliveryRouter(LifecycleContextStore lifecycleContextStore, FeatureRoomStateStore featureRoomStateStore) {
        this.lifecycleContextStore = Objects.requireNonNull(lifecycleContextStore, "lifecycleContextStore");
        this.featureRoomStateStore = featureRoomStateStore;
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

    /**
     * Send using a specific bot's sender (by configured bot id). Used when workflow specifies asBotId.
     */
    public void sendAs(String channelId, String messageId, String content, String asBotId) {
        if (channelId == null || content == null) {
            return;
        }
        if (asBotId == null || asBotId.isBlank()) {
            send(channelId, messageId, content);
            return;
        }
        ReplySender sender = botIdToSender.get(asBotId);
        if (sender == null) {
            log.error("sendAs: no sender registered for bot {}; not sending.", asBotId);
            return;
        }
        sender.send(channelId, messageId, content);
    }

    /**
     * Send as the participant with the given role in the feature room for this delivery target.
     * Resolves FeatureRoomState by channelId (room or thread); finds participant with role; uses that bot's sender.
     * Fallback: when no feature state or role not found, uses standard send (lifecycle/default).
     */
    public void sendAsRole(String channelId, String messageId, String content, PlanningRole role) {
        if (channelId == null || content == null) {
            return;
        }
        if (role == null || featureRoomStateStore == null) {
            send(channelId, messageId, content);
            return;
        }
        Optional<FeatureRoomState> roomOpt = featureRoomStateStore.getByRoomChannelId(channelId);
        if (roomOpt.isEmpty()) {
            roomOpt = featureRoomStateStore.getByDeliveryTargetId(channelId);
        }
        if (roomOpt.isEmpty()) {
            send(channelId, messageId, content);
            return;
        }
        String botId = null;
        for (RoomParticipant p : roomOpt.get().getParticipants()) {
            if (role.equals(p.getRole())) {
                botId = p.getConfiguredBotId();
                break;
            }
        }
        if (botId == null || botId.isBlank()) {
            send(channelId, messageId, content);
            return;
        }
        sendAs(channelId, messageId, content, botId);
    }
}
