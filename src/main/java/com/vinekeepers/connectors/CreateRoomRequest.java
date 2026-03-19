package com.vinekeepers.connectors;

import com.vinekeepers.events.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for SpaceOperations.createRoom. Built from event (sourceId), state, and bind.
 * Explicit intent fields; bind-then-state resolution is performed in from().
 * Optional participantBotIds: when non-empty, permission override is applied for each bot (multi-bot feature room).
 */
public record CreateRoomRequest(
        String sourceId,
        String guildId,
        String channelName,
        String lifecycleOwnerBotId,
        List<String> participantBotIds,
        String project,
        String codeChange) {

    /**
     * Builds a request from event, state, and bind. Bind takes precedence over state for
     * guildId, channelName, lifecycleOwnerBotId, participantBotIds; project and codeChange come from state only.
     */
    public static CreateRoomRequest from(Event event, Map<String, Object> state, Map<String, Object> bind) {
        String sourceId = event != null ? event.getSourceId() : null;
        String guildId = firstNonBlank(getString(bind, "guildId"), getString(state, "guildId"));
        String channelName = firstNonBlank(getString(bind, "channelName"), getString(state, "channelName"));
        String lifecycleOwnerBotId = firstNonBlank(getString(bind, "lifecycleOwnerBotId"), getString(state, "lifecycleOwnerBotId"));
        List<String> participantBotIds = getStringList(bind, "participantBotIds");
        if (participantBotIds.isEmpty()) {
            participantBotIds = getStringList(state, "participantBotIds");
        }
        String project = getString(state, "project");
        String codeChange = getString(state, "codeChange");
        return new CreateRoomRequest(sourceId, guildId, channelName, lifecycleOwnerBotId, participantBotIds, project, codeChange);
    }

    public List<String> participantBotIds() {
        return participantBotIds != null ? List.copyOf(participantBotIds) : List.of();
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getStringList(Map<String, Object> map, String key) {
        if (map == null) return List.of();
        Object v = map.get(key);
        if (v == null) return List.of();
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    out.add(item.toString().trim());
                }
            }
            return Collections.unmodifiableList(out);
        }
        return List.of();
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }
}
