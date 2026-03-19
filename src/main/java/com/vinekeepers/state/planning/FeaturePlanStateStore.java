package com.vinekeepers.state.planning;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for {@link FeaturePlanState}. Indexed by contextId, featureId, roomChannelId, intakeThreadId.
 */
public final class FeaturePlanStateStore {

    private final ConcurrentHashMap<String, FeaturePlanState> byContextId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FeaturePlanState> byFeatureId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FeaturePlanState> byRoomChannelId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FeaturePlanState> byIntakeThreadId = new ConcurrentHashMap<>();

    public void put(FeaturePlanState state) {
        if (state == null) {
            return;
        }
        FeaturePlanState existing = byContextId.get(state.getContextId());
        if (existing != null) {
            removeSecondaryIndexes(existing);
        }
        byContextId.put(state.getContextId(), state);
        if (state.getFeatureId() != null && !state.getFeatureId().isBlank()) {
            byFeatureId.put(state.getFeatureId(), state);
        }
        if (state.getRoomChannelId() != null && !state.getRoomChannelId().isBlank()) {
            byRoomChannelId.put(state.getRoomChannelId(), state);
        }
        if (state.getIntakeThreadId() != null && !state.getIntakeThreadId().isBlank()) {
            byIntakeThreadId.put(state.getIntakeThreadId(), state);
        }
    }

    private void removeSecondaryIndexes(FeaturePlanState existing) {
        if (existing.getFeatureId() != null && !existing.getFeatureId().isBlank()) {
            byFeatureId.remove(existing.getFeatureId());
        }
        if (existing.getRoomChannelId() != null && !existing.getRoomChannelId().isBlank()) {
            byRoomChannelId.remove(existing.getRoomChannelId());
        }
        if (existing.getIntakeThreadId() != null && !existing.getIntakeThreadId().isBlank()) {
            byIntakeThreadId.remove(existing.getIntakeThreadId());
        }
    }

    public Optional<FeaturePlanState> getByContextId(String contextId) {
        if (contextId == null || contextId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byContextId.get(contextId));
    }

    public Optional<FeaturePlanState> getByFeatureId(String featureId) {
        if (featureId == null || featureId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byFeatureId.get(featureId));
    }

    public Optional<FeaturePlanState> getByRoomChannelId(String roomChannelId) {
        if (roomChannelId == null || roomChannelId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byRoomChannelId.get(roomChannelId));
    }

    public Optional<FeaturePlanState> getByIntakeThreadId(String intakeThreadId) {
        if (intakeThreadId == null || intakeThreadId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byIntakeThreadId.get(intakeThreadId));
    }

    /**
     * Alias of {@link #put(FeaturePlanState)} for semantic clarity.
     */
    public void update(FeaturePlanState state) {
        put(state);
    }
}
