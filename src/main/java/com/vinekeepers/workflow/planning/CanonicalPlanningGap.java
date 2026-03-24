package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.CoordinatorClarificationGapRule;

import java.util.Objects;

/**
 * Production representation of unresolved coordinator planning uncertainty. Built from profile rules and plan state;
 * resolution policy decides ask / assume / defer / block.
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
        boolean askable,
        boolean assumable,
        boolean deferable,
        /** Template / seed text from the rule; final user-facing wording comes from {@link PlanningQuestionComposer}. */
        String questionSeed,
        /** Short rationale for logs (not prompt wording). */
        String evidenceSummary) {

    public CanonicalPlanningGap {
        gapId = gapId != null ? gapId.trim() : "";
        artifactPath = artifactPath != null ? artifactPath.trim() : "";
        mergeTargetPath = mergeTargetPath != null ? mergeTargetPath.trim() : "";
        questionSeed = questionSeed != null ? questionSeed : "";
        evidenceSummary = evidenceSummary != null ? evidenceSummary : "";
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(severity, "severity");
    }

    public static CanonicalPlanningGap fromCoordinatorOpenGap(
            CoordinatorClarificationGapEvaluator.OpenGap open,
            CoordinatorClarificationGapRule rule,
            int siblingIndex) {
        if (open == null) {
            throw new IllegalArgumentException("open");
        }
        String ruleId = open.gapId();
        CanonicalGapKind kind = CanonicalGapKind.fromRuleId(ruleId);
        String mergePath = CanonicalMergeTargetPaths.defaultMergeTargetPath(ruleId);
        String gapId = CanonicalPlanningGapIds.stableCoordinatorGapId(kind, mergePath, ruleId, siblingIndex);
        CanonicalGapSeverity severity = open.blocking() ? CanonicalGapSeverity.HIGH : CanonicalGapSeverity.STANDARD;
        String artifactPath = artifactPathFromMergeTarget(mergePath);
        String seed = open.questionText() != null ? open.questionText().trim() : "";
        String evidence = evidenceLine(rule);
        return new CanonicalPlanningGap(
                gapId,
                kind,
                artifactPath,
                mergePath,
                severity,
                open.blocking(),
                true,
                true,
                true,
                seed,
                evidence);
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

    private static String evidenceLine(CoordinatorClarificationGapRule rule) {
        if (rule == null) {
            return "";
        }
        int n = rule.getResolveAnySubstring() != null ? rule.getResolveAnySubstring().size() : 0;
        return "rule=" + rule.getId() + ";resolveMarkers=" + n;
    }
}
