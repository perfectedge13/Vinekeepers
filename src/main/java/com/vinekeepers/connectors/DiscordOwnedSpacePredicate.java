package com.vinekeepers.connectors;

import com.vinekeepers.state.LifecycleContext;
import com.vinekeepers.state.LifecycleContextStore;
import com.vinekeepers.state.planning.FeatureRoomState;
import com.vinekeepers.state.planning.FeatureRoomStateStore;

import java.util.Objects;
import java.util.Optional;

/**
 * True when a Discord channel or thread id is part of feature-room state or legacy lifecycle context for this bot.
 */
public final class DiscordOwnedSpacePredicate {

    private final FeatureRoomStateStore featureRoomStateStore;
    private final LifecycleContextStore lifecycleContextStore;
    private final String botId;

    public DiscordOwnedSpacePredicate(
            FeatureRoomStateStore featureRoomStateStore,
            LifecycleContextStore lifecycleContextStore,
            String botId) {
        this.featureRoomStateStore = featureRoomStateStore;
        this.lifecycleContextStore = lifecycleContextStore;
        this.botId = Objects.requireNonNull(botId, "botId");
    }

    public boolean allowsChannel(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return false;
        }
        if (featureRoomStateStore != null) {
            var byThread = featureRoomStateStore.getByIntakeThreadId(channelId);
            if (byThread.isPresent()) {
                return isParticipant(byThread.get());
            }
            var byRoom = featureRoomStateStore.getByRoomChannelId(channelId);
            if (byRoom.isPresent()) {
                return isParticipant(byRoom.get());
            }
        }
        if (lifecycleContextStore != null) {
            Optional<LifecycleContext> byCh = lifecycleContextStore.getByChannelId(channelId);
            if (byCh.isEmpty()) {
                byCh = lifecycleContextStore.getByDeliveryTargetId(channelId);
            }
            if (byCh.isPresent()) {
                String owner = byCh.get().getConfiguredBotId();
                return owner != null && owner.equals(botId);
            }
        }
        return false;
    }

    private boolean isParticipant(FeatureRoomState state) {
        return featureRoomStateStore.getParticipantBotIds(state).contains(botId);
    }
}
