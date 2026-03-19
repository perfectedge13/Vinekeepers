package com.vinekeepers.state.planning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for FeatureRoomState. Indexed by contextId, roomChannelId, intakeThreadId, featureId.
 */
public final class FeatureRoomStateStore {

    private final ConcurrentHashMap<String, FeatureRoomState> byContextId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FeatureRoomState> byRoomChannelId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FeatureRoomState> byIntakeThreadId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FeatureRoomState> byFeatureId = new ConcurrentHashMap<>();

    public void put(FeatureRoomState state) {
        if (state == null) return;
        byContextId.put(state.getContextId(), state);
        if (state.getRoomChannelId() != null && !state.getRoomChannelId().isBlank()) {
            byRoomChannelId.put(state.getRoomChannelId(), state);
        }
        if (state.getIntakeThreadId() != null && !state.getIntakeThreadId().isBlank()) {
            byIntakeThreadId.put(state.getIntakeThreadId(), state);
        }
        if (state.getFeatureId() != null && !state.getFeatureId().isBlank()) {
            byFeatureId.put(state.getFeatureId(), state);
        }
    }

    public Optional<FeatureRoomState> getByContextId(String contextId) {
        if (contextId == null || contextId.isBlank()) return Optional.empty();
        return Optional.ofNullable(byContextId.get(contextId));
    }

    public Optional<FeatureRoomState> getByRoomChannelId(String roomChannelId) {
        if (roomChannelId == null || roomChannelId.isBlank()) return Optional.empty();
        return Optional.ofNullable(byRoomChannelId.get(roomChannelId));
    }

    public Optional<FeatureRoomState> getByDeliveryTargetId(String deliveryTargetId) {
        if (deliveryTargetId == null || deliveryTargetId.isBlank()) return Optional.empty();
        Optional<FeatureRoomState> byThread = getByIntakeThreadId(deliveryTargetId);
        if (byThread.isPresent()) return byThread;
        return getByRoomChannelId(deliveryTargetId);
    }

    public Optional<FeatureRoomState> getByIntakeThreadId(String intakeThreadId) {
        if (intakeThreadId == null || intakeThreadId.isBlank()) return Optional.empty();
        return Optional.ofNullable(byIntakeThreadId.get(intakeThreadId));
    }

    public Optional<FeatureRoomState> getByFeatureId(String featureId) {
        if (featureId == null || featureId.isBlank()) return Optional.empty();
        return Optional.ofNullable(byFeatureId.get(featureId));
    }

    /**
     * Returns participant configured bot ids in stable order (Orchestrator first, then Architect, Auditor, Scribe).
     * Participants are sorted by PlanningRole.ordinal() before building the list.
     */
    public List<String> getParticipantBotIds(FeatureRoomState state) {
        if (state == null || state.getParticipants().isEmpty()) return List.of();
        List<RoomParticipant> sorted = new ArrayList<>(state.getParticipants());
        sorted.sort(Comparator.comparingInt(p -> p.getRole().ordinal()));
        List<String> ids = new ArrayList<>();
        for (RoomParticipant p : sorted) {
            if (p.getConfiguredBotId() != null && !p.getConfiguredBotId().isBlank()) {
                ids.add(p.getConfiguredBotId());
            }
        }
        return List.copyOf(ids);
    }

    /**
     * Primary coordinator bot id for feature-room routing (room channel): first participant with
     * {@code primaryCoordinator}, else the ORCHESTRATOR participant's configuredBotId.
     */
    public static Optional<String> resolveCoordinatorConfiguredBotId(FeatureRoomState state) {
        if (state == null || state.getParticipants().isEmpty()) {
            return Optional.empty();
        }
        for (RoomParticipant p : state.getParticipants()) {
            if (p.isPrimaryCoordinator()) {
                String id = p.getConfiguredBotId();
                if (id != null && !id.isBlank()) {
                    return Optional.of(id);
                }
            }
        }
        for (RoomParticipant p : state.getParticipants()) {
            if (p.getRole() == PlanningRole.ORCHESTRATOR) {
                String id = p.getConfiguredBotId();
                if (id != null && !id.isBlank()) {
                    return Optional.of(id);
                }
            }
        }
        return Optional.empty();
    }
}
