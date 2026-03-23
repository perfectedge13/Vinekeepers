package com.vinekeepers.connectors;

import com.vinekeepers.state.LifecycleContext;
import com.vinekeepers.state.LifecycleContextStore;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomState;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.planning.PlanningIntakeBindingResolver;

import java.util.Objects;
import java.util.Optional;

/**
 * True when a Discord channel or thread id is part of feature-room state or legacy lifecycle context for this bot.
 */
public final class DiscordOwnedSpacePredicate {

    private final FeatureRoomStateStore featureRoomStateStore;
    private final FeaturePlanStateStore featurePlanStateStore;
    private final LifecycleContextStore lifecycleContextStore;
    private final String botId;

    public DiscordOwnedSpacePredicate(
            FeatureRoomStateStore featureRoomStateStore,
            LifecycleContextStore lifecycleContextStore,
            String botId) {
        this(featureRoomStateStore, lifecycleContextStore, botId, null);
    }

    public DiscordOwnedSpacePredicate(
            FeatureRoomStateStore featureRoomStateStore,
            LifecycleContextStore lifecycleContextStore,
            String botId,
            FeaturePlanStateStore featurePlanStateStore) {
        this.featureRoomStateStore = featureRoomStateStore;
        this.lifecycleContextStore = lifecycleContextStore;
        this.featurePlanStateStore = featurePlanStateStore;
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
        if (featurePlanStateStore != null) {
            Optional<FeaturePlanState> planThread = featurePlanStateStore.getByIntakeThreadId(channelId);
            if (planThread.isPresent()
                    && PlanningIntakeBindingResolver.isActivePlanningIntake(planThread.get())) {
                String c = planThread.get().getCoordinatorConfiguredBotId();
                return c != null && c.equals(botId);
            }
            Optional<FeaturePlanState> planRoom = featurePlanStateStore.getByRoomChannelId(channelId);
            if (planRoom.isPresent()) {
                FeaturePlanState p = planRoom.get();
                if (PlanningIntakeBindingResolver.isActivePlanningIntake(p)
                        && p.getIntakeThreadId() != null
                        && !p.getIntakeThreadId().isBlank()) {
                    String c = p.getCoordinatorConfiguredBotId();
                    return c != null && c.equals(botId);
                }
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
