package com.vinekeepers.workflow;

import com.vinekeepers.events.Event;

/**
 * Shared helpers for workflow capability resolution (e.g. source prefix from event).
 */
public final class WorkflowCapabilitySupport {

    private WorkflowCapabilitySupport() {}

    /**
     * Extracts the source prefix from an event's sourceId (e.g. "discord" from "discord:guild-123").
     * Returns null if event or sourceId is null; otherwise the part before the first colon, or the full trimmed sourceId.
     */
    public static String sourcePrefix(Event event) {
        if (event == null || event.getSourceId() == null) {
            return null;
        }
        String sourceId = event.getSourceId();
        int colon = sourceId.indexOf(':');
        return colon >= 0 ? sourceId.substring(0, colon).trim() : sourceId.trim();
    }
}
