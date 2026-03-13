package com.vinekeepers.state;

import java.time.Instant;
import java.util.Objects;

/**
 * Lifecycle context for a room/channel: indexed by channelId and externalRunId (Cursor agent id).
 * Includes configuredBotId, runtimeBotInstanceId, optional repo/requestText, and minimal status.
 */
public final class LifecycleContext {

    private final String contextId;
    private final String channelId;
    private final Instant createdAt;
    private volatile String externalRunId;
    private final String configuredBotId;
    private final String runtimeBotInstanceId;
    private final String repo;
    private final String requestText;
    private volatile String status;
    private final String deliveryChannelId;

    public LifecycleContext(String contextId, String channelId, Instant createdAt) {
        this(contextId, channelId, createdAt, null, null, null, null, null, null);
    }

    public LifecycleContext(String contextId, String channelId, Instant createdAt, String externalRunId) {
        this(contextId, channelId, createdAt, externalRunId, null, null, null, null, null);
    }

    public LifecycleContext(String contextId, String channelId, Instant createdAt,
                            String externalRunId, String configuredBotId, String runtimeBotInstanceId,
                            String repo, String requestText) {
        this(contextId, channelId, createdAt, externalRunId, configuredBotId, runtimeBotInstanceId, repo, requestText, null);
    }

    public LifecycleContext(String contextId, String channelId, Instant createdAt,
                            String externalRunId, String configuredBotId, String runtimeBotInstanceId,
                            String repo, String requestText, String status) {
        this(contextId, channelId, createdAt, externalRunId, configuredBotId, runtimeBotInstanceId, repo, requestText, status, null);
    }

    public LifecycleContext(String contextId, String channelId, Instant createdAt,
                            String externalRunId, String configuredBotId, String runtimeBotInstanceId,
                            String repo, String requestText, String status, String deliveryChannelId) {
        this.contextId = Objects.requireNonNull(contextId, "contextId");
        this.channelId = Objects.requireNonNull(channelId, "channelId");
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.externalRunId = externalRunId;
        this.configuredBotId = configuredBotId;
        this.runtimeBotInstanceId = runtimeBotInstanceId;
        this.repo = repo;
        this.requestText = requestText;
        this.status = status != null && !status.isBlank() ? status : "active";
        this.deliveryChannelId = deliveryChannelId;
    }

    public String getContextId() {
        return contextId;
    }

    public String getChannelId() {
        return channelId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getExternalRunId() {
        return externalRunId;
    }

    public synchronized void setExternalRunId(String externalRunId) {
        this.externalRunId = externalRunId;
    }

    public String getConfiguredBotId() {
        return configuredBotId;
    }

    public String getRuntimeBotInstanceId() {
        return runtimeBotInstanceId;
    }

    public String getRepo() {
        return repo;
    }

    public String getRequestText() {
        return requestText;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status != null && !status.isBlank() ? status : this.status;
    }

    public String getDeliveryChannelId() {
        return deliveryChannelId;
    }

    /**
     * Returns a new LifecycleContext with the same fields but the given deliveryChannelId.
     * Used to update the store after create_thread stores the thread id.
     */
    public LifecycleContext withDeliveryChannelId(String deliveryChannelId) {
        return new LifecycleContext(contextId, channelId, createdAt, externalRunId, configuredBotId,
                runtimeBotInstanceId, repo, requestText, status, deliveryChannelId);
    }
}
