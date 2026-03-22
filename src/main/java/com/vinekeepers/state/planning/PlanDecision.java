package com.vinekeepers.state.planning;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class PlanDecision {

    private final String id;
    private final String decision;
    private final String rationale;
    private final String status;
    private final String source;
    private final List<String> relatedFieldKeys;
    private final Instant addedAt;

    public PlanDecision(
            String id,
            String decision,
            String rationale,
            String status,
            String source,
            List<String> relatedFieldKeys,
            Instant addedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.decision = decision != null ? decision : "";
        this.rationale = rationale != null ? rationale : "";
        this.status = status != null && !status.isBlank() ? status : PlanRiskDecisionStatus.RESOLVED;
        this.source = source != null ? source : "";
        this.relatedFieldKeys = relatedFieldKeys != null ? List.copyOf(relatedFieldKeys) : List.of();
        this.addedAt = addedAt != null ? addedAt : Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getDecision() {
        return decision;
    }

    public String getRationale() {
        return rationale;
    }

    public String getStatus() {
        return status;
    }

    public String getSource() {
        return source;
    }

    public List<String> getRelatedFieldKeys() {
        return relatedFieldKeys;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanDecision that = (PlanDecision) o;
        return id.equals(that.id)
                && decision.equals(that.decision)
                && rationale.equals(that.rationale)
                && status.equals(that.status)
                && source.equals(that.source)
                && relatedFieldKeys.equals(that.relatedFieldKeys)
                && addedAt.equals(that.addedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, decision, rationale, status, source, relatedFieldKeys, addedAt);
    }
}
