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
    private final String status;
    private final PlanCritiqueRubricScores rubricScores;
    private final int blockingFindingCount;
    private final List<String> requiredRevisions;

    public PlanCritiqueSnapshot(Instant generatedAt, String source, List<PlanCritiqueFinding> findings) {
        this(
                generatedAt,
                source,
                findings,
                PlanCritiqueLifecycleStatus.COMPLETE,
                null,
                countBlocking(findings),
                List.of());
    }

    public PlanCritiqueSnapshot(
            Instant generatedAt,
            String source,
            List<PlanCritiqueFinding> findings,
            String status,
            PlanCritiqueRubricScores rubricScores,
            int blockingFindingCount,
            List<String> requiredRevisions) {
        this.generatedAt = generatedAt != null ? generatedAt : Instant.now();
        this.source = source != null ? source : "";
        this.findings = findings != null ? List.copyOf(findings) : List.of();
        this.status = status != null && !status.isBlank() ? status : PlanCritiqueLifecycleStatus.COMPLETE;
        this.rubricScores = rubricScores;
        this.blockingFindingCount = Math.max(0, blockingFindingCount);
        this.requiredRevisions = requiredRevisions != null ? List.copyOf(requiredRevisions) : List.of();
    }

    private static int countBlocking(List<PlanCritiqueFinding> findings) {
        if (findings == null) {
            return 0;
        }
        int n = 0;
        for (PlanCritiqueFinding f : findings) {
            if (f.isBlocksApproval()) {
                n++;
            }
        }
        return n;
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

    public String getStatus() {
        return status;
    }

    public PlanCritiqueRubricScores getRubricScores() {
        return rubricScores;
    }

    public int getBlockingFindingCount() {
        return blockingFindingCount;
    }

    public List<String> getRequiredRevisions() {
        return requiredRevisions;
    }
}
