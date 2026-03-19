package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.CreateRoomRequest;
import com.vinekeepers.connectors.CreateRoomResult;
import com.vinekeepers.connectors.SpaceOperations;
import com.vinekeepers.connectors.SpaceOperationsRegistry;
import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.WorkflowCapabilitySupport;

import java.util.Map;

/**
 * Workflow action: create a room (e.g. Discord text channel) via the SpaceOperations capability
 * for the event's source. Resolves capability by source prefix (e.g. "discord" from "discord:...").
 * Returns channel id or CHANNEL_CREATE_FAILED on failure or unknown source. Fail-closed: null/blank
 * prefix or unregistered prefix returns CHANNEL_CREATE_FAILED (no implicit default).
 */
public final class CreateChannelAction implements com.vinekeepers.workflow.WorkflowAction {

    /** Sentinel returned when channel creation fails (e.g. for workflow branch). */
    public static final String CHANNEL_CREATE_FAILED = "CHANNEL_CREATE_FAILED";

    private final SpaceOperationsRegistry registry;
    private final SpaceOperations testOverride;

    /** Production: resolve capability by source prefix from registry. */
    public CreateChannelAction(SpaceOperationsRegistry registry) {
        this.registry = registry;
        this.testOverride = null;
    }

    /** Test: use the given ops instead of registry lookup when non-null. */
    public CreateChannelAction(SpaceOperationsRegistry registry, SpaceOperations testOverride) {
        this.registry = registry;
        this.testOverride = testOverride;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        CreateRoomRequest request = CreateRoomRequest.from(event, state, bind);
        if (testOverride != null) {
            CreateRoomResult result = testOverride.createRoom(request);
            return result.isSuccess() ? result.getChannelId() : CHANNEL_CREATE_FAILED;
        }
        String prefix = WorkflowCapabilitySupport.sourcePrefix(event);
        if (prefix == null || prefix.isBlank()) {
            return CHANNEL_CREATE_FAILED;
        }
        SpaceOperations ops = registry != null ? registry.get(prefix) : null;
        if (ops == null) {
            return CHANNEL_CREATE_FAILED;
        }
        CreateRoomResult result = ops.createRoom(request);
        return result.isSuccess() ? result.getChannelId() : CHANNEL_CREATE_FAILED;
    }
}
