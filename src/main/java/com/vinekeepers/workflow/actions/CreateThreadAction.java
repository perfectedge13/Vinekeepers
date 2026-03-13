package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.DiscordGateway;
import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.events.Event;

import java.util.Map;

/**
 * Workflow action: create a Discord thread under a parent text channel (e.g. lifecycle room).
 * Bind/state: channelId (parent), threadName. Uses default gateway. Returns thread id or THREAD_CREATE_FAILED on failure.
 */
public final class CreateThreadAction implements com.vinekeepers.workflow.WorkflowAction {

    /** Sentinel returned when thread creation fails (e.g. for workflow branch or fallback to channel). */
    public static final String THREAD_CREATE_FAILED = "THREAD_CREATE_FAILED";

    private final OutboundDeliveryRouter router;

    public CreateThreadAction(OutboundDeliveryRouter router) {
        this.router = router;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        if (router == null) {
            return THREAD_CREATE_FAILED;
        }
        DiscordGateway gateway = router.getDefaultGateway();
        if (gateway == null || !gateway.isConnected()) {
            return THREAD_CREATE_FAILED;
        }
        String channelId = firstNonBlank(getString(bind, "channelId"), state != null ? getString(state, "channelId") : null);
        if (channelId == null || channelId.isBlank()) {
            return THREAD_CREATE_FAILED;
        }
        String threadName = firstNonBlank(getString(bind, "threadName"), state != null ? getString(state, "threadName") : null);
        if (threadName == null || threadName.isBlank()) {
            threadName = "Room updates";
        }
        String threadId = gateway.createThreadChannel(channelId, threadName);
        return threadId != null ? threadId : THREAD_CREATE_FAILED;
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
