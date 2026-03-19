package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeatureRoomState;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.planning.PlanningRole;
import com.vinekeepers.state.planning.RoomParticipant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Workflow action: read featureRoomParticipants from state, validate shape and size (4 entries, five keys each),
 * build FeatureRoomState and put in FeatureRoomStateStore. Bind/state: contextId, roomChannelId (channelId),
 * intakeThreadId (deliveryChannelId), repo, initialRequest (codeChange), featureId, featureSlug, createdBy.
 */
public final class InitializeFeatureRoomStateAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final String KEY_PARTICIPANTS = "featureRoomParticipants";
    private static final int EXPECTED_SIZE = 4;
    private static final List<String> REQUIRED_KEYS = List.of("role", "configuredBotId", "runtimeBotInstanceId", "displayName", "primaryCoordinator");

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
        if (list.size() != EXPECTED_SIZE) {
            return "Missing or invalid featureRoomParticipants (expected size 4, got " + list.size() + ").";
        }
        List<RoomParticipant> participants = new ArrayList<>();
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
            String configuredBotId = nullToBlank(entry.get("configuredBotId"));
            String runtimeBotInstanceId = nullToBlank(entry.get("runtimeBotInstanceId"));
            if (configuredBotId.isBlank() || runtimeBotInstanceId.isBlank()) {
                return "Missing or invalid featureRoomParticipants (configuredBotId and runtimeBotInstanceId must be non-blank).";
            }
            String displayName = entry.get("displayName") != null ? entry.get("displayName").toString() : configuredBotId;
            boolean primaryCoordinator = Boolean.TRUE.equals(entry.get("primaryCoordinator"));
            participants.add(new RoomParticipant(role, configuredBotId, runtimeBotInstanceId, displayName, primaryCoordinator));
        }

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

        // When featureId is null/blank, generate "feat-" + 12 hex for a stable unique id.
        if (featureId == null || featureId.isBlank()) {
            featureId = "feat-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        // When featureSlug is null/blank, derive from initialRequest (sanitized, short) or repo or fallback "feature". Preserve when supplied.
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

    /**
     * Derives a short slug from initialRequest (sanitized) or repo, or returns "feature" as fallback.
     */
    private static String deriveFeatureSlug(String initialRequest, String repo) {
        String src = (initialRequest != null && !initialRequest.isBlank()) ? initialRequest : (repo != null ? repo : "");
        if (src.isBlank()) return "feature";
        String sanitized = src.replaceAll("[^a-zA-Z0-9_-]", "-").replaceAll("-+", "-").trim();
        if (sanitized.isBlank()) return "feature";
        // Keep short: first 32 chars; strip leading/trailing dashes
        String segment = sanitized.length() > 32 ? sanitized.substring(0, 32) : sanitized;
        int start = 0, end = segment.length();
        while (start < end && segment.charAt(start) == '-') start++;
        while (end > start && segment.charAt(end - 1) == '-') end--;
        String result = start < end ? segment.substring(start, end) : "";
        return result.isBlank() ? "feature" : result;
    }
}
