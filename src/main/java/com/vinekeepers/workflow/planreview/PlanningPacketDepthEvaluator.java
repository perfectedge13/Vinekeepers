package com.vinekeepers.workflow.planreview;

import com.vinekeepers.state.planning.FeaturePlanState;

/**
 * Heuristic depth check before posting the full planning packet to Discord.
 */
public final class PlanningPacketDepthEvaluator {

    /** Minimum whitespace-delimited tokens for request exploration body. */
    public static final int MIN_EXPLORATION_WORDS = 24;
    /** Minimum for current state or feature summary (either one satisfying is enough). */
    public static final int MIN_NARRATIVE_WORDS = 14;

    private PlanningPacketDepthEvaluator() {}

    public record DepthResult(boolean ok, String reason) {}

    public static DepthResult evaluate(FeaturePlanState plan) {
        if (plan == null) {
            return new DepthResult(false, "No plan loaded.");
        }
        String exploration = PlanningArtifactTexts.artifactField(plan, "request_exploration", "analysis", "exploration_body");
        String current = PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "current_state_summary");
        String feature = PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "feature_summary");
        int exWords = wordCount(exploration);
        int curWords = wordCount(current);
        int featWords = wordCount(feature);
        if (exWords < MIN_EXPLORATION_WORDS) {
            return new DepthResult(
                    false,
                    "Request exploration is too thin (" + exWords + " words; need at least " + MIN_EXPLORATION_WORDS + ").");
        }
        if (curWords < MIN_NARRATIVE_WORDS && featWords < MIN_NARRATIVE_WORDS) {
            return new DepthResult(
                    false,
                    "Current state and feature summary are both thin ("
                            + curWords
                            + " / "
                            + featWords
                            + " words; need at least "
                            + MIN_NARRATIVE_WORDS
                            + " in one).");
        }
        return new DepthResult(true, "Depth OK (exploration " + exWords + " words).");
    }

    static int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }
}
