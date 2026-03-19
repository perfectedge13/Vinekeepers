package com.vinekeepers.connectors;

import com.vinekeepers.events.Event;

import java.util.Map;

/**
 * Request DTO for SpaceOperations.createThread. Built from event (sourceId), state, and bind.
 * Explicit intent fields; bind-then-state resolution is performed in from().
 */
public record CreateThreadRequest(
        String sourceId,
        String channelId,
        String threadName,
        String contextId) {

    /**
     * Builds a request from event, state, and bind. Bind takes precedence over state for
     * channelId, threadName, and contextId.
     */
    public static CreateThreadRequest from(Event event, Map<String, Object> state, Map<String, Object> bind) {
        String sourceId = event != null ? event.getSourceId() : null;
        String channelId = firstNonBlank(getString(bind, "channelId"), getString(state, "channelId"));
        String threadName = firstNonBlank(getString(bind, "threadName"), getString(state, "threadName"));
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        return new CreateThreadRequest(sourceId, channelId, threadName, contextId);
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
