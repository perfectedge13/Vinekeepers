package com.vinekeepers.state.planning;

import java.time.Instant;
import java.util.List;

/**
 * Persisted result of a plan critique run (Phase C).
 */
public final class PlanCritiqueSnapshot {

    private final Instant generatedAt;
    private final String source;
    private final List<PlanCritiqueFinding> findings;

    public PlanCritiqueSnapshot(Instant generatedAt, String source, List<PlanCritiqueFinding> findings) {
        this.generatedAt = generatedAt != null ? generatedAt : Instant.now();
        this.source = source != null ? source : "";
        this.findings = findings != null ? List.copyOf(findings) : List.of();
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public String getSource() {
        return source;
    }

    public List<PlanCritiqueFinding> getFindings() {
        return findings;
    }
}
