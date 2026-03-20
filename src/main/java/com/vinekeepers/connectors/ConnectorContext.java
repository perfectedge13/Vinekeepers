package com.vinekeepers.connectors;

import com.vinekeepers.events.EventBus;
import com.vinekeepers.state.LifecycleContextStore;
import com.vinekeepers.state.planning.FeatureRoomStateStore;

import java.util.Objects;

/**
 * Generic context passed to connector adapters: shared runtime dependencies only.
 * No connector-specific or Discord-named fields; Discord-specific options are passed
 * via adapter constructor or config (e.g. DiscordConnectorConfig).
 * Environment variables are read via com.vinekeepers.env.Env.get() where needed.
 */
public final class ConnectorContext {

    private final EventBus eventBus;
    private final OutboundDeliveryRouter outboundDeliveryRouter;
    private final FeatureRoomStateStore featureRoomStateStore;
    private final LifecycleContextStore lifecycleContextStore;

    public ConnectorContext(EventBus eventBus, OutboundDeliveryRouter outboundDeliveryRouter) {
        this(eventBus, outboundDeliveryRouter, null, null);
    }

    public ConnectorContext(
            EventBus eventBus,
            OutboundDeliveryRouter outboundDeliveryRouter,
            FeatureRoomStateStore featureRoomStateStore,
            LifecycleContextStore lifecycleContextStore) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.outboundDeliveryRouter = Objects.requireNonNull(outboundDeliveryRouter, "outboundDeliveryRouter");
        this.featureRoomStateStore = featureRoomStateStore;
        this.lifecycleContextStore = lifecycleContextStore;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public OutboundDeliveryRouter getOutboundDeliveryRouter() {
        return outboundDeliveryRouter;
    }

    public FeatureRoomStateStore getFeatureRoomStateStore() {
        return featureRoomStateStore;
    }

    public LifecycleContextStore getLifecycleContextStore() {
        return lifecycleContextStore;
    }
}
