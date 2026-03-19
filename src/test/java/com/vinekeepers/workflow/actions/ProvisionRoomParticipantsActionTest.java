package com.vinekeepers.workflow.actions;

import com.vinekeepers.bot.RuntimeBotInstance;
import com.vinekeepers.events.Event;
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
        assertEquals("Missing instanceId for provision_room_participants (provision_bot_instance for arrietty must run first).", result);
    }

    @Test
    void runReturnsFourParticipantsWithExpectedShape() {
        StateStore stateStore = new StateStore();
        ProvisionRoomParticipantsAction action = new ProvisionRoomParticipantsAction(stateStore);
        String orchestratorInstanceId = "arrietty-reuse123";
        Object result = action.run(null,
                Map.of("channelId", "ch-room", "instanceId", orchestratorInstanceId),
                Map.of("channelId", "ch-room", "instanceId", orchestratorInstanceId));

        assertTrue(result instanceof List);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> participants = (List<Map<String, Object>>) result;
        assertEquals(4, participants.size());

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

        Map<String, Object> architect = participants.get(1);
        assertEquals(PlanningRole.ARCHITECT.name(), architect.get("role"));
        assertEquals("architect", architect.get("configuredBotId"));
        assertTrue(architect.get("runtimeBotInstanceId").toString().startsWith("architect-"));
        assertEquals("Architect", architect.get("displayName"));
        assertEquals(Boolean.FALSE, architect.get("primaryCoordinator"));

        Map<String, Object> auditor = participants.get(2);
        assertEquals(PlanningRole.AUDITOR.name(), auditor.get("role"));
        assertEquals("auditor", auditor.get("configuredBotId"));

        Map<String, Object> scribe = participants.get(3);
        assertEquals(PlanningRole.SCRIBE.name(), scribe.get("role"));
        assertEquals("scribe", scribe.get("configuredBotId"));
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
    void runStoresNewInstancesForArchitectAuditorScribeInStateStore() {
        StateStore stateStore = new StateStore();
        ProvisionRoomParticipantsAction action = new ProvisionRoomParticipantsAction(stateStore);
        action.run(null,
                Map.of("channelId", "ch-1", "instanceId", "arrietty-xxxxxxxx"),
                Map.of());

        List<String> botInstanceKeys = stateStore.keys().stream()
                .filter(k -> k.startsWith("bot_instance:"))
                .toList();
        assertEquals(3, botInstanceKeys.size(), "architect, auditor, scribe get new instances stored");
        assertTrue(botInstanceKeys.stream().anyMatch(k -> k.contains("architect-")));
        assertTrue(botInstanceKeys.stream().anyMatch(k -> k.contains("auditor-")));
        assertTrue(botInstanceKeys.stream().anyMatch(k -> k.contains("scribe-")));
    }
}
