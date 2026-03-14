package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.connectors.OutboundGateway;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.LifecycleContextStore;

import java.util.Map;

/**
 * Workflow action: create a Discord thread under a parent text channel (e.g. lifecycle room).
 * Bind/state: channelId (parent), threadName, optional contextId. When channelId is set and that channel has a
 * lifecycle context, uses that context's bot gateway (e.g. Arrietty); otherwise uses default gateway.
 * After successful creation, updates the lifecycle context's delivery target via the store when contextId is in state.
 * Returns thread id or THREAD_CREATE_FAILED on failure.
 */
public final class CreateThreadAction implements com.vinekeepers.workflow.WorkflowAction {

    /** Sentinel returned when thread creation fails (e.g. for workflow branch or fallback to channel). */
    public static final String THREAD_CREATE_FAILED = "THREAD_CREATE_FAILED";

    private final OutboundDeliveryRouter router;
    private final LifecycleContextStore lifecycleContextStore;

    public CreateThreadAction(OutboundDeliveryRouter router, LifecycleContextStore lifecycleContextStore) {
        this.router = router;
        this.lifecycleContextStore = lifecycleContextStore;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        if (router == null) {
            return THREAD_CREATE_FAILED;
        }
        String channelId = firstNonBlank(getString(bind, "channelId"), state != null ? getString(state, "channelId") : null);
        if (channelId == null || channelId.isBlank()) {
            return THREAD_CREATE_FAILED;
        }
        OutboundGateway gateway = (channelId != null && !channelId.isBlank())
                ? router.getGatewayForChannel(channelId)
                : null;
        if (gateway == null) {
            gateway = router.getDefaultGateway();
        }
        if (gateway == null || !gateway.isConnected()) {
            return THREAD_CREATE_FAILED;
        }
        String threadName = firstNonBlank(getString(bind, "threadName"), state != null ? getString(state, "threadName") : null);
        if (threadName == null || threadName.isBlank()) {
            threadName = "Room updates";
        }
        String threadId = gateway.createThreadChannel(channelId, threadName);
        Object result = threadId != null ? threadId : THREAD_CREATE_FAILED;
        if (threadId != null && !threadId.isBlank() && lifecycleContextStore != null) {
            String contextId = firstNonBlank(getString(bind, "contextId"), state != null ? getString(state, "contextId") : null);
            if (contextId != null && !contextId.isBlank()) {
                lifecycleContextStore.setDeliveryTargetId(contextId, threadId);
            }
        }
        return result;
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }
}
