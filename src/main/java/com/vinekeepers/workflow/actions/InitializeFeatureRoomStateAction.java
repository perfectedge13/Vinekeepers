package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeatureRoomState;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.planning.PlanningRole;
import com.vinekeepers.state.planning.RoomParticipant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Workflow action: read featureRoomParticipants from state (transient list of maps), validate the Arrietty-only
 * room contract (exactly one ORCHESTRATOR marked primaryCoordinator; non-blank configuredBotId/runtimeBotInstanceId),
 * convert to typed {@link RoomParticipant}, build {@link FeatureRoomState}, and put it in store.
 */
public final class InitializeFeatureRoomStateAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final String KEY_PARTICIPANTS = "featureRoomParticipants";
    private static final List<String> REQUIRED_KEYS = List.of("role", "configuredBotId", "runtimeBotInstanceId", "primaryCoordinator");

    private final FeatureRoomStateStore featureRoomStateStore;

    public InitializeFeatureRoomStateAction(FeatureRoomStateStore featureRoomStateStore) {
        this.featureRoomStateStore = featureRoomStateStore;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        if (featureRoomStateStore == null) {
            return "FeatureRoomStateStore not available.";
        }
        Object raw = state != null ? state.get(KEY_PARTICIPANTS) : null;
        if (raw == null) {
            raw = bind != null ? bind.get(KEY_PARTICIPANTS) : null;
        }
        if (!(raw instanceof List<?> list)) {
            return "Missing or invalid featureRoomParticipants (must be a list).";
        }
        List<RoomParticipant> participants = new ArrayList<>();
        EnumMap<PlanningRole, Integer> roleCounts = new EnumMap<>(PlanningRole.class);
        int primaryTrueCount = 0;

        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                return "Missing or invalid featureRoomParticipants (each entry must be a map).";
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> entry = (Map<String, Object>) map;
            for (String key : REQUIRED_KEYS) {
                if (!entry.containsKey(key)) {
                    return "Missing or invalid featureRoomParticipants (missing key: " + key + ").";
                }
            }
            String roleStr = String.valueOf(entry.get("role"));
            PlanningRole role = parseRole(roleStr);
            if (role == null) {
                return "Missing or invalid featureRoomParticipants (invalid role: " + roleStr + ").";
            }
            roleCounts.merge(role, 1, Integer::sum);
            if (roleCounts.get(role) > 1) {
                return "Missing or invalid featureRoomParticipants (duplicate role: " + role.name() + ").";
            }

            String configuredBotId = nullToBlank(entry.get("configuredBotId"));
            String runtimeBotInstanceId = nullToBlank(entry.get("runtimeBotInstanceId"));
            if (configuredBotId.isBlank() || runtimeBotInstanceId.isBlank()) {
                return "Missing or invalid featureRoomParticipants (configuredBotId and runtimeBotInstanceId must be non-blank).";
            }
            String displayName = entry.containsKey("displayName") && entry.get("displayName") != null
                    ? entry.get("displayName").toString().trim()
                    : "";
            if (displayName.isBlank()) {
                displayName = configuredBotId;
            }
            boolean primaryCoordinator = Boolean.TRUE.equals(entry.get("primaryCoordinator"));
            if (primaryCoordinator) {
                primaryTrueCount++;
                if (role != PlanningRole.ORCHESTRATOR) {
                    return "Missing or invalid featureRoomParticipants (only ORCHESTRATOR may be primaryCoordinator).";
                }
            }
            participants.add(new RoomParticipant(role, configuredBotId, runtimeBotInstanceId, displayName, primaryCoordinator));
        }

        if (roleCounts.getOrDefault(PlanningRole.ORCHESTRATOR, 0) != 1) {
            return "Missing or invalid featureRoomParticipants (role contract requires exactly one ORCHESTRATOR).";
        }
        if (primaryTrueCount != 1) {
            return "Missing or invalid featureRoomParticipants (exactly one primaryCoordinator required on ORCHESTRATOR).";
        }
        if (list.size() != 1) {
            return "Missing or invalid featureRoomParticipants (expected 1 entry, got " + list.size() + ").";
        }

        participants.sort(Comparator.comparingInt(p -> p.getRole().ordinal()));

        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        String roomChannelId = firstNonBlank(getString(bind, "roomChannelId"), getString(state, "channelId"));
        String intakeThreadId = firstNonBlank(getString(bind, "intakeThreadId"), getString(state, "deliveryChannelId"));
        String repo = firstNonBlank(getString(bind, "repo"), getString(state, "project"));
        String initialRequest = firstNonBlank(getString(bind, "initialRequest"), getString(state, "codeChange"));
        String featureId = firstNonBlank(getString(bind, "featureId"), getString(state, "featureId"));
        String featureSlug = firstNonBlank(getString(bind, "featureSlug"), getString(state, "featureSlug"));
        String createdBy = firstNonBlank(getString(bind, "createdBy"), getString(state, "createdBy"));

        if (contextId == null || contextId.isBlank()) {
            return "Missing contextId for initialize_feature_room_state.";
        }
        if (roomChannelId == null || roomChannelId.isBlank()) {
            return "Missing roomChannelId/channelId for initialize_feature_room_state.";
        }

        if (featureId == null || featureId.isBlank()) {
            featureId = "feat-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        if (featureSlug == null || featureSlug.isBlank()) {
            featureSlug = deriveFeatureSlug(initialRequest, repo);
        }

        FeatureRoomState roomState = new FeatureRoomState(
                contextId, featureId, featureSlug, roomChannelId, intakeThreadId,
                repo, initialRequest, "INTAKE_READY", participants, createdBy, null);
        featureRoomStateStore.put(roomState);
        return "OK";
    }

    private static PlanningRole parseRole(String s) {
        if (s == null || s.isBlank()) return null;
        String upper = s.trim().toUpperCase();
        for (PlanningRole r : PlanningRole.values()) {
            if (r.name().equals(upper)) return r;
        }
        return null;
    }

    private static String nullToBlank(Object o) {
        return o != null ? o.toString().trim() : "";
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }

    private static String deriveFeatureSlug(String initialRequest, String repo) {
        String src = (initialRequest != null && !initialRequest.isBlank()) ? initialRequest : (repo != null ? repo : "");
        if (src.isBlank()) return "feature";
        String sanitized = src.replaceAll("[^a-zA-Z0-9_-]", "-").replaceAll("-+", "-").trim();
        if (sanitized.isBlank()) return "feature";
        String segment = sanitized.length() > 32 ? sanitized.substring(0, 32) : sanitized;
        int start = 0, end = segment.length();
        while (start < end && segment.charAt(start) == '-') start++;
        while (end > start && segment.charAt(end - 1) == '-') end--;
        String result = start < end ? segment.substring(start, end) : "";
        return result.isBlank() ? "feature" : result;
    }
}
