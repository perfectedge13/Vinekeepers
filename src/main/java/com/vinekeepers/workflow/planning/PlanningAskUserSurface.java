package com.vinekeepers.workflow.planning;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the canonical gap and plain question text for coordinator clarification when evaluation routes ASK_USER.
 * Fills in when {@link PlanningEvaluationDecision#chosenAskGap()} is null but routing still requires one question.
 */
public final class PlanningAskUserSurface {

    /** Stable id for synthetic recovery gaps (merge target uses {@link CanonicalMergeTargetPaths#defaultMergeTargetPath}). */
    public static final String RECOVERY_GAP_ID = "planning_eval_recovery";

    private PlanningAskUserSurface() {}

    public static CanonicalPlanningGap resolveAskGap(PlanningEvaluationDecision decision) {
        if (decision == null) {
            return recoveryGap("");
        }
        CanonicalPlanningGap chosen = decision.chosenAskGap();
        if (chosen != null) {
            return chosen;
        }
        CanonicalPlanningGap sole = soleEligibleAskGap(decision.gaps());
        if (sole != null) {
            return sole;
        }
        return recoveryGap(decision.topGapText());
    }

    public static String resolveQuestionText(PlanningDecisionSnapshot snapshot, PlanningEvaluationDecision decision) {
        if (snapshot != null) {
            String s = snapshot.nextQuestion();
            if (s != null && !s.isBlank()) {
                return s.trim();
            }
        }
        if (decision != null) {
            String t = decision.canonicalQuestionText();
            if (t != null && !t.isBlank()) {
                return t.trim();
            }
        }
        return "";
    }

    private static CanonicalPlanningGap soleEligibleAskGap(List<CanonicalPlanningGap> gaps) {
        if (gaps == null || gaps.isEmpty()) {
            return null;
        }
        List<CanonicalPlanningGap> eligible = new ArrayList<>();
        for (CanonicalPlanningGap g : gaps) {
            if (g != null && g.askable() && (g.blocking() || g.branching())) {
                eligible.add(g);
            }
        }
        return eligible.size() == 1 ? eligible.get(0) : null;
    }

    private static CanonicalPlanningGap recoveryGap(String seed) {
        String desc =
                seed != null && !seed.isBlank()
                        ? seed.trim()
                        : "Please confirm one open planning choice so we can proceed.";
        return CanonicalPlanningGap.fromEvaluation(
                RECOVERY_GAP_ID, "BRANCHING_DECISION", desc, false, true, false, "", List.of());
    }
}
