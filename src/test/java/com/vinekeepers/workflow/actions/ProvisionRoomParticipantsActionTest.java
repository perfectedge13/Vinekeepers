package com.vinekeepers.workflow.actions;

import com.vinekeepers.bot.RuntimeBotInstance;
import com.vinekeepers.state.StateStore;
import com.vinekeepers.state.planning.PlanningRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProvisionRoomParticipantsActionTest {

    @Test
    void runReturnsErrorWhenStateStoreNull() {
        ProvisionRoomParticipantsAction action = new ProvisionRoomParticipantsAction(null);
        Object result = action.run(null, Map.of(), Map.of("channelId", "ch-1", "instanceId", "arrietty-abc12345"));
        assertEquals("State store not available.", result);
    }

    @Test
    void runReturnsErrorWhenChannelIdMissing() {
        ProvisionRoomParticipantsAction action = new ProvisionRoomParticipantsAction(new StateStore());
        Object result = action.run(null, Map.of("instanceId", "arrietty-x"), Map.of());
        assertEquals("Missing channelId for provision_room_participants.", result);
    }

    @Test
    void runReturnsErrorWhenInstanceIdMissing() {
        ProvisionRoomParticipantsAction action = new ProvisionRoomParticipantsAction(new StateStore());
        Object result = action.run(null, Map.of("channelId", "ch-1"), Map.of());
        assertEquals("Missing valid Arrietty runtime instance for provision_room_participants "
                + "(run provision_bot_instance for arrietty first, or resolve a single Arrietty instance for this channel).", result);
    }

    @Test
    void runReturnsCoordinatorParticipantWithExpectedShape() {
        StateStore stateStore = new StateStore();
        String orchestratorInstanceId = "arrietty-reuse123";
        stateStore.put("bot_instance:" + orchestratorInstanceId,
                new RuntimeBotInstance(orchestratorInstanceId, "arrietty", "Arrietty", "ch-room"));
        ProvisionRoomParticipantsAction action = new ProvisionRoomParticipantsAction(stateStore);
        Object result = action.run(null,
                Map.of("channelId", "ch-room", "instanceId", orchestratorInstanceId),
                Map.of("channelId", "ch-room", "instanceId", orchestratorInstanceId));

        assertTrue(result instanceof List);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> participants = (List<Map<String, Object>>) result;
        assertEquals(1, participants.size());

        List<String> requiredKeys = List.of("role", "configuredBotId", "runtimeBotInstanceId", "displayName", "primaryCoordinator");
        for (Map<String, Object> entry : participants) {
            for (String key : requiredKeys) {
                assertTrue(entry.containsKey(key), "missing key: " + key);
            }
        }

        Map<String, Object> orch = participants.get(0);
        assertEquals(PlanningRole.ORCHESTRATOR.name(), orch.get("role"));
        assertEquals("arrietty", orch.get("configuredBotId"));
        assertEquals(orchestratorInstanceId, orch.get("runtimeBotInstanceId"));
        assertEquals("Arrietty", orch.get("displayName"));
        assertEquals(Boolean.TRUE, orch.get("primaryCoordinator"));

    }

    @Test
    void runOrchestratorReusesInstanceIdFromState() {
        StateStore stateStore = new StateStore();
        String existingInstanceId = "arrietty-abcdef12";
        stateStore.put("bot_instance:" + existingInstanceId,
                new RuntimeBotInstance(existingInstanceId, "arrietty", "Arrietty", "ch-1"));
        ProvisionRoomParticipantsAction action = new ProvisionRoomParticipantsAction(stateStore);

        Object result = action.run(null, Map.of("channelId", "ch-1", "instanceId", existingInstanceId), Map.of());
        assertTrue(result instanceof List);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> participants = (List<Map<String, Object>>) result;
        assertEquals(existingInstanceId, participants.get(0).get("runtimeBotInstanceId"));
    }

    @Test
    void runDoesNotProvisionAdditionalPlanningBotInstances() {
        StateStore stateStore = new StateStore();
        String orchId = "arrietty-xxxxxxxx";
        stateStore.put("bot_instance:" + orchId,
                new RuntimeBotInstance(orchId, "arrietty", "Arrietty", "ch-1"));
        ProvisionRoomParticipantsAction action = new ProvisionRoomParticipantsAction(stateStore);
        action.run(null,
                Map.of("channelId", "ch-1", "instanceId", orchId),
                Map.of());

        List<String> botInstanceKeys = stateStore.keys().stream()
                .filter(k -> k.startsWith("bot_instance:"))
                .toList();
        assertEquals(1, botInstanceKeys.size(), "only the pre-seeded Arrietty instance should remain");
    }

    @Test
    void runReturnsErrorWhenMultipleArriettyInstancesOnChannelAndInstanceIdUnresolved() {
        StateStore stateStore = new StateStore();
        stateStore.put("bot_instance:" + "arrietty-11111111",
                new RuntimeBotInstance("arrietty-11111111", "arrietty", "Arrietty", "ch-dup"));
        stateStore.put("bot_instance:" + "arrietty-22222222",
                new RuntimeBotInstance("arrietty-22222222", "arrietty", "Arrietty", "ch-dup"));
        ProvisionRoomParticipantsAction action = new ProvisionRoomParticipantsAction(stateStore);
        Object result = action.run(null, Map.of("channelId", "ch-dup"), Map.of());
        assertEquals("Multiple Arrietty runtime instances for this channel; cannot select ORCHESTRATOR instance.", result);
    }

    @Test
    void runPicksSoleArriettyOnChannelWhenInstanceIdOmitted() {
        StateStore stateStore = new StateStore();
        String soleId = "arrietty-only1234";
        stateStore.put("bot_instance:" + soleId,
                new RuntimeBotInstance(soleId, "arrietty", "Arrietty", "ch-solo"));
        ProvisionRoomParticipantsAction action = new ProvisionRoomParticipantsAction(stateStore);
        Object result = action.run(null, Map.of("channelId", "ch-solo"), Map.of());
        assertTrue(result instanceof List);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> participants = (List<Map<String, Object>>) result;
        assertEquals(soleId, participants.get(0).get("runtimeBotInstanceId"));
    }
}
