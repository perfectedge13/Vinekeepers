package com.vinekeepers.state.planning;

import java.util.Objects;

/**
 * Immutable participant in a feature room: role, configured bot id, runtime instance id,
 * display name, and whether this participant is the primary coordinator (Orchestrator).
 */
public final class RoomParticipant {

    private final PlanningRole role;
    private final String configuredBotId;
    private final String runtimeBotInstanceId;
    private final String displayName;
    private final boolean primaryCoordinator;

    public RoomParticipant(PlanningRole role, String configuredBotId, String runtimeBotInstanceId,
                          String displayName, boolean primaryCoordinator) {
        this.role = Objects.requireNonNull(role, "role");
        this.configuredBotId = Objects.requireNonNull(configuredBotId, "configuredBotId");
        this.runtimeBotInstanceId = Objects.requireNonNull(runtimeBotInstanceId, "runtimeBotInstanceId");
        this.displayName = displayName != null ? displayName : configuredBotId;
        this.primaryCoordinator = primaryCoordinator;
    }

    public PlanningRole getRole() {
        return role;
    }

    public String getConfiguredBotId() {
        return configuredBotId;
    }

    public String getRuntimeBotInstanceId() {
        return runtimeBotInstanceId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isPrimaryCoordinator() {
        return primaryCoordinator;
    }
}
