package com.vinekeepers.workflow.actions;

import com.vinekeepers.bot.BotCatalog;
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
 * <p>Reuses {@code state.instanceId} from a prior {@code provision_bot_instance} when valid, or the sole
 * existing runtime instance for the configured template bot on the channel. Returns a one-entry
 * {@code featureRoomParticipants} list with role, configuredBotId, runtimeBotInstanceId, displayName,
 * and primaryCoordinator.
 */
public final class ProvisionRoomParticipantsAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final String PREFIX = "bot_instance:";

    private final StateStore stateStore;
    private final BotCatalog botCatalog;

    public ProvisionRoomParticipantsAction(StateStore stateStore, BotCatalog botCatalog) {
        this.stateStore = stateStore;
        this.botCatalog = botCatalog != null ? botCatalog : new BotCatalog();
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

        Optional<String> templateOpt = resolveTemplateBotId(state, bind);
        if (templateOpt.isEmpty()) {
            return "Missing orchestrator bot id for provision_room_participants "
                    + "(bind or state: orchestratorConfiguredBotId, templateBotId, valid instanceId, or __botId).";
        }
        String templateBotId = templateOpt.get();

        String orchestratorInstanceId = getString(bind, "instanceId");
        if (orchestratorInstanceId == null || orchestratorInstanceId.isBlank()) {
            orchestratorInstanceId = getString(state, "instanceId");
        }

        List<RuntimeBotInstance> onChannel = listTemplateInstancesForChannel(templateBotId, channelId);
        Optional<String> resolvedOrch = resolveOrchestratorRuntimeInstanceId(
                templateBotId, channelId, orchestratorInstanceId, onChannel);
        if (resolvedOrch.isEmpty()) {
            if (onChannel.size() > 1) {
                return "Multiple runtime instances for template bot " + templateBotId
                        + " on this channel; cannot select ORCHESTRATOR instance.";
            }
            return "Missing valid runtime instance for provision_room_participants "
                    + "(run provision_bot_instance for " + templateBotId + " first, or resolve a single instance for this channel).";
        }
        orchestratorInstanceId = resolvedOrch.get();

        String displayName = firstNonBlank(
                getString(bind, "orchestratorDisplayName"),
                getString(state, "orchestratorDisplayName"));
        if (displayName == null || displayName.isBlank()) {
            displayName = botCatalog.displayNameForBot(templateBotId);
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = templateBotId;
        }

        List<Map<String, Object>> participants = new ArrayList<>();
        Map<String, Object> orch = entry(
                PlanningRole.ORCHESTRATOR.name(), templateBotId, orchestratorInstanceId, displayName, true);
        participants.add(orch);

        return participants;
    }

    private Optional<String> resolveTemplateBotId(Map<String, Object> state, Map<String, Object> bind) {
        String explicit = firstNonBlank(
                firstNonBlank(
                        getString(bind, "orchestratorConfiguredBotId"),
                        getString(state, "orchestratorConfiguredBotId")),
                firstNonBlank(getString(bind, "templateBotId"), getString(state, "templateBotId")));
        if (explicit != null && !explicit.isBlank()) {
            return Optional.of(explicit.trim());
        }
        String instanceId = firstNonBlank(getString(bind, "instanceId"), getString(state, "instanceId"));
        if (instanceId != null && !instanceId.isBlank()) {
            Optional<RuntimeBotInstance> stored = stateStore.get(PREFIX + instanceId.trim(), RuntimeBotInstance.class);
            if (stored.isPresent()) {
                String tid = stored.get().getTemplateBotId();
                if (tid != null && !tid.isBlank()) {
                    return Optional.of(tid.trim());
                }
            }
        }
        String fromSession = getString(state, "__botId");
        if (fromSession != null && !fromSession.isBlank()) {
            return Optional.of(fromSession.trim());
        }
        return Optional.empty();
    }

    private Optional<String> resolveOrchestratorRuntimeInstanceId(
            String templateBotId,
            String channelId,
            String instanceIdFromState,
            List<RuntimeBotInstance> onChannel) {
        if (instanceIdFromState != null && !instanceIdFromState.isBlank()) {
            Optional<RuntimeBotInstance> stored = stateStore.get(PREFIX + instanceIdFromState, RuntimeBotInstance.class);
            if (stored.isPresent()
                    && templateBotId.equalsIgnoreCase(stored.get().getTemplateBotId())
                    && channelMatches(stored.get(), channelId)) {
                return Optional.of(instanceIdFromState);
            }
        }
        if (onChannel.size() == 1) {
            return Optional.of(onChannel.get(0).getInstanceId());
        }
        return Optional.empty();
    }

    private List<RuntimeBotInstance> listTemplateInstancesForChannel(String templateBotId, String channelId) {
        return stateStore.keys().stream()
                .filter(k -> k != null && k.startsWith(PREFIX))
                .map(k -> stateStore.get(k, RuntimeBotInstance.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(inst -> templateBotId.equalsIgnoreCase(inst.getTemplateBotId()))
                .filter(inst -> channelMatches(inst, channelId))
                .collect(Collectors.toList());
    }

    private static boolean channelMatches(RuntimeBotInstance inst, String channelId) {
        if (inst.getChannelId() == null || inst.getChannelId().isBlank()) {
            return true;
        }
        return channelId.equals(inst.getChannelId());
    }

    private static Map<String, Object> entry(
            String role, String configuredBotId, String runtimeBotInstanceId, String displayName, boolean primaryCoordinator) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("configuredBotId", configuredBotId);
        m.put("runtimeBotInstanceId", runtimeBotInstanceId);
        m.put("displayName", displayName);
        m.put("primaryCoordinator", primaryCoordinator);
        return m;
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
