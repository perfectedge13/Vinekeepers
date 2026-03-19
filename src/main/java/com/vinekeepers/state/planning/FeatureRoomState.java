package com.vinekeepers.state.planning;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable state for a multi-bot feature room: contextId, featureId, featureSlug,
 * roomChannelId, intakeThreadId, repo, initialRequest, status, participants, createdBy, createdAt.
 */
public final class FeatureRoomState {

    private final String contextId;
    private final String featureId;
    private final String featureSlug;
    private final String roomChannelId;
    private final String intakeThreadId;
    private final String repo;
    private final String initialRequest;
    private final String status;
    private final List<RoomParticipant> participants;
    private final String createdBy;
    private final Instant createdAt;

    public FeatureRoomState(String contextId, String featureId, String featureSlug,
                            String roomChannelId, String intakeThreadId, String repo,
                            String initialRequest, String status, List<RoomParticipant> participants,
                            String createdBy, Instant createdAt) {
        this.contextId = Objects.requireNonNull(contextId, "contextId");
        this.featureId = featureId;
        this.featureSlug = featureSlug;
        this.roomChannelId = Objects.requireNonNull(roomChannelId, "roomChannelId");
        this.intakeThreadId = intakeThreadId;
        this.repo = repo;
        this.initialRequest = initialRequest;
        this.status = status != null && !status.isBlank() ? status : "INTAKE_READY";
        this.participants = participants != null ? List.copyOf(participants) : List.of();
        this.createdBy = createdBy;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public String getContextId() {
        return contextId;
    }

    public String getFeatureId() {
        return featureId;
    }

    public String getFeatureSlug() {
        return featureSlug;
    }

    public String getRoomChannelId() {
        return roomChannelId;
    }

    public String getIntakeThreadId() {
        return intakeThreadId;
    }

    public String getRepo() {
        return repo;
    }

    public String getInitialRequest() {
        return initialRequest;
    }

    public String getStatus() {
        return status;
    }

    public List<RoomParticipant> getParticipants() {
        return participants;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
