package com.vinekeepers.state.planning;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Tracked planning assumption with explicit status and provenance. */
public final class PlanAssumption {

    private final String id;
    private final String statement;
    private final String status;
    private final String severity;
    private final String source;
    private final String owner;
    private final List<String> relatedFieldKeys;
    private final Instant addedAt;
    private final Instant updatedAt;

    public PlanAssumption(
            String id,
            String statement,
            String status,
            String severity,
            String source,
            String owner,
            List<String> relatedFieldKeys,
            Instant addedAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.statement = statement != null ? statement : "";
        this.status = status != null && !status.isBlank() ? status : PlanAssumptionStatus.OPEN;
        this.severity = severity != null && !severity.isBlank() ? severity : PlanGovernanceSeverity.MEDIUM;
        this.source = source != null ? source : "";
        this.owner = owner != null ? owner : "";
        this.relatedFieldKeys = relatedFieldKeys != null ? List.copyOf(relatedFieldKeys) : List.of();
        Instant now = Instant.now();
        this.addedAt = addedAt != null ? addedAt : now;
        this.updatedAt = updatedAt != null ? updatedAt : this.addedAt;
    }

    /** Backward-compatible factory for legacy call sites (plain text, defaults). */
    public static PlanAssumption fromLegacyText(String id, String text, Instant addedAt) {
        return new PlanAssumption(
                id,
                text,
                PlanAssumptionStatus.OPEN,
                PlanGovernanceSeverity.MEDIUM,
                "LEGACY",
                "",
                List.of(),
                addedAt,
                addedAt);
    }

    /** Recorded from an explicit user/coordinator clarification choice. */
    public static PlanAssumption fromUserClarification(String id, String statement, Instant at) {
        return new PlanAssumption(
                id,
                statement,
                PlanAssumptionStatus.CONFIRMED,
                PlanGovernanceSeverity.MEDIUM,
                "COORDINATOR",
                "",
                List.of(),
                at,
                at);
    }

    public String getId() {
        return id;
    }

    public String getStatement() {
        return statement;
    }

    /** @deprecated Prefer {@link #getStatement()} */
    @Deprecated
    public String getText() {
        return statement;
    }

    public String getStatus() {
        return status;
    }

    public String getSeverity() {
        return severity;
    }

    public String getSource() {
        return source;
    }

    public String getOwner() {
        return owner;
    }

    public List<String> getRelatedFieldKeys() {
        return relatedFieldKeys;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public PlanAssumption withStatus(String newStatus) {
        return new PlanAssumption(
                id, statement, newStatus, severity, source, owner, relatedFieldKeys, addedAt, Instant.now());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanAssumption that = (PlanAssumption) o;
        return id.equals(that.id)
                && statement.equals(that.statement)
                && status.equals(that.status)
                && severity.equals(that.severity)
                && source.equals(that.source)
                && owner.equals(that.owner)
                && relatedFieldKeys.equals(that.relatedFieldKeys)
                && addedAt.equals(that.addedAt)
                && updatedAt.equals(that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, statement, status, severity, source, owner, relatedFieldKeys, addedAt, updatedAt);
    }
}
