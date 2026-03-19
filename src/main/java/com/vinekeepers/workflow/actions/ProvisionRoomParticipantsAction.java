package com.vinekeepers.workflow.actions;

import com.vinekeepers.bot.RuntimeBotInstance;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.StateStore;
import com.vinekeepers.state.planning.PlanningRole;
import com.vinekeepers.state.planning.RoomParticipant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Workflow action: provision the four feature-room participants (Orchestrator reuses state.instanceId
 * from prior provision_bot_instance; provisions architect, auditor, scribe). Returns List&lt;Map&lt;String,Object&gt;&gt;
 * for storeIn: featureRoomParticipants. Each entry has role, configuredBotId, runtimeBotInstanceId, displayName,
 * primaryCoordinator. Also stores RuntimeBotInstance in StateStore under bot_instance:{runtimeBotInstanceId}.
 */
public final class ProvisionRoomParticipantsAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final String PREFIX = "bot_instance:";

    private static final String ORCHESTRATOR_BOT_ID = "arrietty";
    private static final String ARCHITECT_BOT_ID = "architect";
    private static final String AUDITOR_BOT_ID = "auditor";
    private static final String SCRIBE_BOT_ID = "scribe";

    private final StateStore stateStore;

    public ProvisionRoomParticipantsAction(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        if (stateStore == null) {
            return "State store not available.";
        }
        String channelId = getString(bind, "channelId");
        if (channelId == null || channelId.isBlank()) {
            channelId = getString(state, "channelId");
        }
        if (channelId == null || channelId.isBlank()) {
            return "Missing channelId for provision_room_participants.";
        }
        String orchestratorInstanceId = getString(bind, "instanceId");
        if (orchestratorInstanceId == null || orchestratorInstanceId.isBlank()) {
            orchestratorInstanceId = getString(state, "instanceId");
        }
        if (orchestratorInstanceId == null || orchestratorInstanceId.isBlank()) {
            return "Missing instanceId for provision_room_participants (provision_bot_instance for arrietty must run first).";
        }

        List<Map<String, Object>> participants = new ArrayList<>();

        // Orchestrator (Arrietty): reuse existing instance from state
        Map<String, Object> orch = entry(PlanningRole.ORCHESTRATOR.name(), ORCHESTRATOR_BOT_ID, orchestratorInstanceId, "Arrietty", true);
        participants.add(orch);
        // No new instance to store for orchestrator; already stored by provision_bot_instance

        // Architect, Auditor, Scribe: provision new instances
        for (String botId : new String[]{ARCHITECT_BOT_ID, AUDITOR_BOT_ID, SCRIBE_BOT_ID}) {
            String displayName = displayNameFor(botId);
            String instanceId = botId + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            RuntimeBotInstance instance = new RuntimeBotInstance(instanceId, botId, displayName, channelId);
            stateStore.put(PREFIX + instanceId, instance);
            boolean primary = false;
            PlanningRole role = roleFor(botId);
            participants.add(entry(role.name(), botId, instanceId, displayName, primary));
        }

        return participants;
    }

    private static String displayNameFor(String botId) {
        if (ARCHITECT_BOT_ID.equals(botId)) return "Architect";
        if (AUDITOR_BOT_ID.equals(botId)) return "Auditor";
        if (SCRIBE_BOT_ID.equals(botId)) return "Scribe";
        return botId;
    }

    private static PlanningRole roleFor(String botId) {
        if (ARCHITECT_BOT_ID.equals(botId)) return PlanningRole.ARCHITECT;
        if (AUDITOR_BOT_ID.equals(botId)) return PlanningRole.AUDITOR;
        if (SCRIBE_BOT_ID.equals(botId)) return PlanningRole.SCRIBE;
        return PlanningRole.ORCHESTRATOR;
    }

    private static Map<String, Object> entry(String role, String configuredBotId, String runtimeBotInstanceId, String displayName, boolean primaryCoordinator) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("configuredBotId", configuredBotId);
        m.put("runtimeBotInstanceId", runtimeBotInstanceId);
        m.put("displayName", displayName);
        m.put("primaryCoordinator", primaryCoordinator);
        return m;
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
