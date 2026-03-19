package com.vinekeepers.state.planning;

import java.time.Instant;
import java.util.Objects;

/**
 * Optional lightweight approval placeholder for the plan.
 */
public final class PlanApproval {

    private final String status;
    private final String actorId;
    private final Instant at;

    public PlanApproval(String status, String actorId, Instant at) {
        this.status = status;
        this.actorId = actorId;
        this.at = at;
    }

    public String getStatus() {
        return status;
    }

    public String getActorId() {
        return actorId;
    }

    public Instant getAt() {
        return at;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanApproval that = (PlanApproval) o;
        return Objects.equals(status, that.status)
                && Objects.equals(actorId, that.actorId)
                && Objects.equals(at, that.at);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, actorId, at);
    }
}
