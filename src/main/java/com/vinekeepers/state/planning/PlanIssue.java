package com.vinekeepers.state.planning;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Tracked planning issue / blocker with explicit status. */
public final class PlanIssue {

    private final String id;
    private final String title;
    private final String detail;
    private final String status;
    private final String severity;
    private final String source;
    private final String owner;
    private final List<String> relatedFieldKeys;
    private final Instant addedAt;
    private final Instant updatedAt;

    public PlanIssue(
            String id,
            String title,
            String detail,
            String status,
            String severity,
            String source,
            String owner,
            List<String> relatedFieldKeys,
            Instant addedAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = title != null ? title : "";
        this.detail = detail != null ? detail : "";
        this.status = status != null && !status.isBlank() ? status : PlanIssueStatus.OPEN;
        this.severity = severity != null && !severity.isBlank() ? severity : PlanGovernanceSeverity.MEDIUM;
        this.source = source != null ? source : "";
        this.owner = owner != null ? owner : "";
        this.relatedFieldKeys = relatedFieldKeys != null ? List.copyOf(relatedFieldKeys) : List.of();
        Instant now = Instant.now();
        this.addedAt = addedAt != null ? addedAt : now;
        this.updatedAt = updatedAt != null ? updatedAt : this.addedAt;
    }

    /** Legacy single-line issue text as title. */
    public static PlanIssue fromLegacyText(String id, String text, Instant addedAt) {
        String t = text != null ? text.trim() : "";
        return new PlanIssue(
                id,
                t,
                "",
                PlanIssueStatus.OPEN,
                PlanGovernanceSeverity.MEDIUM,
                "LEGACY",
                "",
                List.of(),
                addedAt,
                addedAt);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    /** @deprecated Prefer {@link #getTitle()} / {@link #getDetail()} */
    @Deprecated
    public String getText() {
        if (!detail.isBlank()) {
            return title.isBlank() ? detail : title + ": " + detail;
        }
        return title;
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

    public boolean isBlocking() {
        return PlanIssueStatus.BLOCKING.equalsIgnoreCase(status);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanIssue planIssue = (PlanIssue) o;
        return id.equals(planIssue.id)
                && title.equals(planIssue.title)
                && detail.equals(planIssue.detail)
                && status.equals(planIssue.status)
                && severity.equals(planIssue.severity)
                && source.equals(planIssue.source)
                && owner.equals(planIssue.owner)
                && relatedFieldKeys.equals(planIssue.relatedFieldKeys)
                && addedAt.equals(planIssue.addedAt)
                && updatedAt.equals(planIssue.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id, title, detail, status, severity, source, owner, relatedFieldKeys, addedAt, updatedAt);
    }
}
