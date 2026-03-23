package com.vinekeepers.workflow.actions;

import com.vinekeepers.bot.RuntimeBotInstance;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.StateStore;
import com.vinekeepers.state.planning.PlanningRole;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Workflow action: provision the feature-room coordinator participant.
 *
 * <p>Arrietty reuses {@code state.instanceId} from a prior {@code provision_bot_instance} when valid, or the sole
 * existing Arrietty runtime instance for the channel. Returns a one-entry {@code featureRoomParticipants} list with
 * role, configuredBotId, runtimeBotInstanceId, displayName, and primaryCoordinator.
 */
public final class ProvisionRoomParticipantsAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final String PREFIX = "bot_instance:";

    private static final String ORCHESTRATOR_BOT_ID = "arrietty";

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

        List<RuntimeBotInstance> arriettyOnChannel = listArriettyInstancesForChannel(channelId);
        Optional<String> resolvedOrch = resolveOrchestratorRuntimeInstanceId(channelId, orchestratorInstanceId, arriettyOnChannel);
        if (resolvedOrch.isEmpty()) {
            if (arriettyOnChannel.size() > 1) {
                return "Multiple Arrietty runtime instances for this channel; cannot select ORCHESTRATOR instance.";
            }
            return "Missing valid Arrietty runtime instance for provision_room_participants "
                    + "(run provision_bot_instance for arrietty first, or resolve a single Arrietty instance for this channel).";
        }
        orchestratorInstanceId = resolvedOrch.get();

        List<Map<String, Object>> participants = new ArrayList<>();

        Map<String, Object> orch = entry(PlanningRole.ORCHESTRATOR.name(), ORCHESTRATOR_BOT_ID, orchestratorInstanceId, "Arrietty", true);
        participants.add(orch);

        return participants;
    }

    /**
     * Reuses {@code instanceIdFromState} when it references a stored Arrietty instance for {@code channelId};
     * otherwise uses the unique Arrietty runtime instance for that channel when exactly one exists.
     */
    private Optional<String> resolveOrchestratorRuntimeInstanceId(String channelId, String instanceIdFromState,
                                                                  List<RuntimeBotInstance> arriettyOnChannel) {
        if (instanceIdFromState != null && !instanceIdFromState.isBlank()) {
            Optional<RuntimeBotInstance> stored = stateStore.get(PREFIX + instanceIdFromState, RuntimeBotInstance.class);
            if (stored.isPresent()
                    && ORCHESTRATOR_BOT_ID.equalsIgnoreCase(stored.get().getTemplateBotId())
                    && channelMatches(stored.get(), channelId)) {
                return Optional.of(instanceIdFromState);
            }
        }
        if (arriettyOnChannel.size() == 1) {
            return Optional.of(arriettyOnChannel.get(0).getInstanceId());
        }
        return Optional.empty();
    }

    private List<RuntimeBotInstance> listArriettyInstancesForChannel(String channelId) {
        return stateStore.keys().stream()
                .filter(k -> k != null && k.startsWith(PREFIX))
                .map(k -> stateStore.get(k, RuntimeBotInstance.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(inst -> ORCHESTRATOR_BOT_ID.equalsIgnoreCase(inst.getTemplateBotId()))
                .filter(inst -> channelMatches(inst, channelId))
                .collect(Collectors.toList());
    }

    private static boolean channelMatches(RuntimeBotInstance inst, String channelId) {
        if (inst.getChannelId() == null || inst.getChannelId().isBlank()) {
            return true;
        }
        return channelId.equals(inst.getChannelId());
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
