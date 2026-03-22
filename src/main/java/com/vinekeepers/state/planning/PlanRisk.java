package com.vinekeepers.state.planning;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class PlanRisk {

    private final String id;
    private final String statement;
    private final String impact;
    private final String likelihood;
    private final String status;
    private final String source;
    private final List<String> relatedFieldKeys;
    private final Instant addedAt;

    public PlanRisk(
            String id,
            String statement,
            String impact,
            String likelihood,
            String status,
            String source,
            List<String> relatedFieldKeys,
            Instant addedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.statement = statement != null ? statement : "";
        this.impact = impact != null ? impact : "";
        this.likelihood = likelihood != null ? likelihood : "";
        this.status = status != null && !status.isBlank() ? status : PlanRiskDecisionStatus.OPEN;
        this.source = source != null ? source : "";
        this.relatedFieldKeys = relatedFieldKeys != null ? List.copyOf(relatedFieldKeys) : List.of();
        this.addedAt = addedAt != null ? addedAt : Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getStatement() {
        return statement;
    }

    public String getImpact() {
        return impact;
    }

    public String getLikelihood() {
        return likelihood;
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
        PlanRisk planRisk = (PlanRisk) o;
        return id.equals(planRisk.id)
                && statement.equals(planRisk.statement)
                && impact.equals(planRisk.impact)
                && likelihood.equals(planRisk.likelihood)
                && status.equals(planRisk.status)
                && source.equals(planRisk.source)
                && relatedFieldKeys.equals(planRisk.relatedFieldKeys)
                && addedAt.equals(planRisk.addedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, statement, impact, likelihood, status, source, relatedFieldKeys, addedAt);
    }
}
