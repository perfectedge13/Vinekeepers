package com.vinekeepers.state;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store for lifecycle contexts with indexes by channelId, externalRunId, and delivery target id.
 */
public final class LifecycleContextStore {

    private static final String THREAD_CREATE_FAILED = "THREAD_CREATE_FAILED";

    private final ConcurrentHashMap<String, LifecycleContext> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> channelToContextId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> externalRunIdToContextId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> deliveryTargetToContextId = new ConcurrentHashMap<>();

    public void put(LifecycleContext context) {
        if (context == null) return;
        LifecycleContext existing = byId.get(context.getContextId());
        if (existing != null && existing.getDeliveryChannelId() != null && !existing.getDeliveryChannelId().isBlank()) {
            deliveryTargetToContextId.remove(existing.getDeliveryChannelId());
        }
        byId.put(context.getContextId(), context);
        if (context.getChannelId() != null && !context.getChannelId().isBlank()) {
            channelToContextId.put(context.getChannelId(), context.getContextId());
        }
        if (context.getExternalRunId() != null && !context.getExternalRunId().isBlank()) {
            externalRunIdToContextId.put(context.getExternalRunId(), context.getContextId());
        }
        if (context.getDeliveryChannelId() != null && !context.getDeliveryChannelId().isBlank()
                && !THREAD_CREATE_FAILED.equals(context.getDeliveryChannelId())) {
            deliveryTargetToContextId.put(context.getDeliveryChannelId(), context.getContextId());
        }
    }

    public Optional<LifecycleContext> getByContextId(String contextId) {
        return Optional.ofNullable(byId.get(contextId));
    }

    public Optional<LifecycleContext> getByChannelId(String channelId) {
        if (channelId == null || channelId.isBlank()) return Optional.empty();
        String cid = channelToContextId.get(channelId);
        return cid != null ? Optional.ofNullable(byId.get(cid)) : Optional.empty();
    }

    /**
     * Resolve context by room channel id or by registered delivery target id (e.g. thread id).
     */
    public Optional<LifecycleContext> getByDeliveryTargetId(String targetId) {
        if (targetId == null || targetId.isBlank()) return Optional.empty();
        Optional<LifecycleContext> byChannel = getByChannelId(targetId);
        if (byChannel.isPresent()) return byChannel;
        String cid = deliveryTargetToContextId.get(targetId);
        return cid != null ? Optional.ofNullable(byId.get(cid)) : Optional.empty();
    }

    public Optional<LifecycleContext> getByExternalRunId(String externalRunId) {
        if (externalRunId == null || externalRunId.isBlank()) return Optional.empty();
        String cid = externalRunIdToContextId.get(externalRunId);
        return cid != null ? Optional.ofNullable(byId.get(cid)) : Optional.empty();
    }

    /**
     * Bind an external run id to an existing context (e.g. after launch_cursor_run).
     * Updates the context and the externalRunId index.
     */
    public void bindExternalRunId(String contextId, String externalRunId) {
        if (contextId == null || externalRunId == null || externalRunId.isBlank()) return;
        LifecycleContext ctx = byId.get(contextId);
        if (ctx != null) {
            ctx.setExternalRunId(externalRunId);
            externalRunIdToContextId.put(externalRunId, contextId);
        }
    }

    public Set<String> contextIds() {
        return Set.copyOf(byId.keySet());
    }
}
