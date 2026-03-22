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
    private final String rationale;

    public PlanApproval(String status, String actorId, Instant at) {
        this(status, actorId, at, null);
    }

    public PlanApproval(String status, String actorId, Instant at, String rationale) {
        this.status = status;
        this.actorId = actorId;
        this.at = at;
        this.rationale = rationale != null ? rationale : "";
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

    /** User-visible explanation (rejection, needs discovery, etc.). */
    public String getRationale() {
        return rationale;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanApproval that = (PlanApproval) o;
        return Objects.equals(status, that.status)
                && Objects.equals(actorId, that.actorId)
                && Objects.equals(at, that.at)
                && Objects.equals(rationale, that.rationale);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, actorId, at, rationale);
    }
}
