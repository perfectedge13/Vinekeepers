package com.vinekeepers.workflow.actions;

import com.vinekeepers.bot.RuntimeBotInstance;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.StateStore;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProvisionBotInstanceActionTest {

    @Test
    void runReturnsErrorWhenStateStoreNull() {
        ProvisionBotInstanceAction action = new ProvisionBotInstanceAction(null);
        Object result = action.run(null, Map.of(), Map.of("templateBotId", "arrietty", "channelId", "chan-1"));
        assertEquals("State store not available.", result);
    }

    @Test
    void runReturnsErrorWhenTemplateBotIdMissing() {
        ProvisionBotInstanceAction action = new ProvisionBotInstanceAction(new StateStore());
        Object result = action.run(null, Map.of(), Map.of());
        assertEquals("Missing templateBotId for provision_bot_instance.", result);
    }

    @Test
    void runCreatesInstanceAndStoresInState() {
        StateStore store = new StateStore();
        ProvisionBotInstanceAction action = new ProvisionBotInstanceAction(store);
        Object result = action.run(null,
                Map.of("templateBotId", "arrietty", "channelId", "chan-1", "displayName", "Luna Helper"),
                Map.of("templateBotId", "arrietty", "channelId", "chan-1", "displayName", "Luna Helper"));
        assertTrue(result instanceof String);
        String instanceId = (String) result;
        assertTrue(instanceId.startsWith("arrietty-"), "instance id must start with templateBotId-");
        assertTrue(instanceId.length() >= "arrietty".length() + 1 + 8, "instance id is templateBotId-<8 hex chars>");
        RuntimeBotInstance instance = store.get("bot_instance:" + instanceId, RuntimeBotInstance.class).orElseThrow();
        assertEquals(instanceId, instance.getInstanceId());
        assertEquals("arrietty", instance.getTemplateBotId());
        assertEquals("Luna Helper", instance.getDisplayName());
        assertEquals("chan-1", instance.getChannelId());
    }

    @Test
    void runInstanceIdUsesGenericTemplateBotIdPrefix() {
        StateStore store = new StateStore();
        ProvisionBotInstanceAction action = new ProvisionBotInstanceAction(store);
        Object result = action.run(null,
                Map.of("templateBotId", "luna", "channelId", "ch"),
                Map.of("templateBotId", "luna", "channelId", "ch"));
        assertTrue(result instanceof String);
        String instanceId = (String) result;
        assertTrue(instanceId.startsWith("luna-"), "instance id must start with templateBotId-");
        assertTrue(instanceId.length() == "luna".length() + 1 + 8, "instance id is templateBotId-<8 hex chars>");
    }

    @Test
    void runUsesDisplayNameFromStateWhenNotInBind() {
        StateStore store = new StateStore();
        ProvisionBotInstanceAction action = new ProvisionBotInstanceAction(store);
        action.run(null, Map.of("templateBotId", "arrietty", "channelId", "ch", "displayName", "Custom"), Map.of("templateBotId", "arrietty", "channelId", "ch"));
        String key = store.keys().stream().filter(k -> k.startsWith("bot_instance:")).findFirst().orElseThrow();
        RuntimeBotInstance instance = store.get(key, RuntimeBotInstance.class).orElseThrow();
        assertEquals("Custom", instance.getDisplayName());
    }

    @Test
    void runDefaultsDisplayNameToTemplateBotId() {
        StateStore store = new StateStore();
        ProvisionBotInstanceAction action = new ProvisionBotInstanceAction(store);
        action.run(null, Map.of("templateBotId", "arrietty", "channelId", "ch"), Map.of("templateBotId", "arrietty", "channelId", "ch"));
        String key = store.keys().stream().filter(k -> k.startsWith("bot_instance:")).findFirst().orElseThrow();
        RuntimeBotInstance instance = store.get(key, RuntimeBotInstance.class).orElseThrow();
        assertEquals("arrietty", instance.getDisplayName());
    }
}
