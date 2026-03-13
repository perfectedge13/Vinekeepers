package com.vinekeepers.workflow.actions;

import com.vinekeepers.bot.RuntimeBotInstance;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.StateStore;

import java.util.Map;
import java.util.UUID;

/**
 * Workflow action: provision a runtime bot instance from a template. Bind/state: templateBotId, channelId, displayName.
 * Returns instance id. Stores instance in state under bot_instance:{instanceId}.
 */
public final class ProvisionBotInstanceAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final String PREFIX = "bot_instance:";

    private final StateStore stateStore;

    public ProvisionBotInstanceAction(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        if (stateStore == null) {
            return "State store not available.";
        }
        String templateBotId = getString(bind, "templateBotId");
        if (templateBotId == null || templateBotId.isBlank()) {
            templateBotId = getString(state, "templateBotId");
        }
        if (templateBotId == null || templateBotId.isBlank()) {
            return "Missing templateBotId for provision_bot_instance.";
        }
        String channelId = getString(bind, "channelId");
        if (channelId == null || channelId.isBlank()) {
            channelId = getString(state, "channelId");
        }
        String displayName = getString(bind, "displayName");
        if (displayName == null || displayName.isBlank()) {
            displayName = getString(state, "displayName");
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = templateBotId;
        }
        String instanceId = templateBotId + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        RuntimeBotInstance instance = new RuntimeBotInstance(instanceId, templateBotId, displayName, channelId);
        stateStore.put(PREFIX + instanceId, instance);
        return instanceId;
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
