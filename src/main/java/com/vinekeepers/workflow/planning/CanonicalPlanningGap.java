package com.vinekeepers.workflow.planning;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Production representation of unresolved planning uncertainty sourced from
 * {@link PlanningEvaluationService} and deterministic merge metadata.
 */
public record CanonicalPlanningGap(
        String gapId,
        CanonicalGapKind kind,
        /** Coarse artifact anchor, e.g. {@code decision_log/decisions}. */
        String artifactPath,
        /** Profile-relative merge slice {@code artifactId/sectionId/fieldKey}. */
        String mergeTargetPath,
        CanonicalGapSeverity severity,
        boolean blocking,
        boolean branching,
        boolean askable,
        boolean assumable,
        boolean deferable,
        String resolutionReason,
        String questionIntent,
        List<String> evidenceSources,
        /** Seed text supplied by planning evaluation for the one allowed user question. */
        String questionSeed,
        /** Short rationale for logs (not prompt wording). */
        String evidenceSummary) {

    public CanonicalPlanningGap(
            String gapId,
            CanonicalGapKind kind,
            String artifactPath,
            String mergeTargetPath,
            CanonicalGapSeverity severity,
            boolean blocking,
            boolean askable,
            boolean assumable,
            boolean deferable,
            String questionSeed,
            String evidenceSummary) {
        this(
                gapId,
                kind,
                artifactPath,
                mergeTargetPath,
                severity,
                blocking,
                kind == CanonicalGapKind.BRANCHING_DECISION,
                askable,
                assumable,
                deferable,
                "",
                "",
                List.of(),
                questionSeed,
                evidenceSummary);
    }

    public CanonicalPlanningGap {
        gapId = gapId != null ? gapId.trim() : "";
        artifactPath = artifactPath != null ? artifactPath.trim() : "";
        mergeTargetPath = mergeTargetPath != null ? mergeTargetPath.trim() : "";
        resolutionReason = resolutionReason != null ? resolutionReason.trim() : "";
        questionIntent = questionIntent != null ? questionIntent.trim() : "";
        evidenceSources = evidenceSources != null ? List.copyOf(evidenceSources) : List.of();
        questionSeed = questionSeed != null ? questionSeed : "";
        evidenceSummary = evidenceSummary != null ? evidenceSummary : "";
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(severity, "severity");
    }

    public static CanonicalPlanningGap fromEvaluation(
            String gapId,
            String kindText,
            String description,
            boolean blocking,
            boolean askable,
            boolean assumable,
            String mergeTargetPath,
            List<String> evidenceSources) {
        CanonicalGapKind kind = CanonicalGapKind.fromRuleId(kindText);
        if (kind == CanonicalGapKind.COORDINATOR_OTHER && kindText != null && !kindText.isBlank()) {
            try {
                kind = CanonicalGapKind.valueOf(kindText.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        String mergePath =
                mergeTargetPath != null && !mergeTargetPath.isBlank()
                        ? mergeTargetPath.trim()
                        : CanonicalMergeTargetPaths.defaultMergeTargetPath(gapId);
        return new CanonicalPlanningGap(
                gapId,
                kind,
                artifactPathFromMergeTarget(mergePath),
                mergePath,
                blocking ? CanonicalGapSeverity.HIGH : CanonicalGapSeverity.STANDARD,
                blocking,
                kind == CanonicalGapKind.BRANCHING_DECISION,
                askable,
                assumable,
                !blocking,
                blocking ? "Blocking or branching gap requires explicit resolution." : "Safe to continue without user input.",
                kind == CanonicalGapKind.BRANCHING_DECISION ? "CHOOSE_DIRECTION" : "UNBLOCK_PROGRESS",
                evidenceSources,
                description,
                description != null ? description : "");
    }

    /** Stable gap ids for ledger reconciliation (order preserved). */
    public static Set<String> openGapIds(List<CanonicalPlanningGap> gaps) {
        if (gaps == null || gaps.isEmpty()) {
            return Set.of();
        }
        Set<String> s = new LinkedHashSet<>();
        for (CanonicalPlanningGap g : gaps) {
            if (g != null && g.gapId() != null && !g.gapId().isBlank()) {
                s.add(g.gapId().trim());
            }
        }
        return s;
    }

    private static String artifactPathFromMergeTarget(String mergeTargetPath) {
        if (mergeTargetPath == null || mergeTargetPath.isBlank()) {
            return "";
        }
        String[] p = mergeTargetPath.split("/");
        if (p.length >= 2) {
            return p[0].trim() + "/" + p[1].trim();
        }
        return mergeTargetPath.trim();
    }

}
