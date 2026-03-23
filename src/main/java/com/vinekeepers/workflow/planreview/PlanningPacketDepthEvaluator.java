package com.vinekeepers.workflow.planreview;

import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.workflow.planning.PlanningPlaceholderDetection;
import com.vinekeepers.workflow.readiness.GenericReadinessEvaluator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Depth and quality gate before treating the planning packet as ready to post.
 */
public final class PlanningPacketDepthEvaluator {

    /** Minimum whitespace-delimited tokens for request exploration body. */
    public static final int MIN_EXPLORATION_WORDS = 24;
    /** Minimum for current state or feature summary (either one satisfying is enough). */
    public static final int MIN_NARRATIVE_WORDS = 14;
    /** Minimum words for open questions when present. */
    public static final int MIN_OPEN_QUESTIONS_WORDS = 8;
    private static final double MAX_ECHO_OVERLAP = 0.72;

    private static final Pattern TOKEN = Pattern.compile("\\w+", Pattern.UNICODE_CHARACTER_CLASS);

    private PlanningPacketDepthEvaluator() {}

    public record DepthResult(boolean ok, String reason) {}

    public static DepthResult evaluate(FeaturePlanState plan) {
        return evaluate(plan, null);
    }

    /**
     * When {@code profile} has declarative readiness ({@link WorkProfileDefinition#hasDeclarativeReadiness()}),
     * runs {@link GenericReadinessEvaluator} first, then supplemental checks (workspace, open questions, validation).
     * Otherwise runs the legacy all-in-one depth gate (v1 profiles).
     */
    public static DepthResult evaluate(FeaturePlanState plan, WorkProfileDefinition profile) {
        if (plan == null) {
            return new DepthResult(false, "No plan loaded.");
        }
        if (profile != null && profile.hasDeclarativeReadiness()) {
            DepthResult declarative = GenericReadinessEvaluator.evaluate(profile, plan);
            if (!declarative.ok()) {
                return declarative;
            }
            return supplementalAfterDeclarative(plan);
        }
        return evaluateLegacy(plan);
    }

    private static DepthResult evaluateLegacy(FeaturePlanState plan) {
        String request = plan.getInitialRequest() != null ? plan.getInitialRequest().trim() : "";
        Set<String> requestTokens = significantTokens(request);

        String exploration = PlanningArtifactTexts.artifactField(plan, "request_exploration", "analysis", "exploration_body");
        String current = PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "current_state_summary");
        String feature = PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "feature_summary");
        String comps = PlanningArtifactTexts.artifactField(plan, "architecture_notes", "impact", "components_impacted");
        String openQ = PlanningArtifactTexts.effectiveOpenQuestions(plan);
        String validation = PlanningArtifactTexts.artifactField(plan, "validation_plan", "checks", "validation_notes");

        int exWords = wordCount(exploration);
        int curWords = wordCount(current);
        int featWords = wordCount(feature);

        if (PlanningPlaceholderDetection.looksLikePlaceholder(exploration)
                || PlanningPlaceholderDetection.looksLikeHollowExploration(exploration)) {
            return new DepthResult(false, "Request exploration reads as placeholder or template.");
        }
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
        if (PlanningPlaceholderDetection.looksLikePlaceholder(current) || PlanningPlaceholderDetection.looksLikePlaceholder(feature)) {
            return new DepthResult(false, "Narrative fields still use hollow seed phrases.");
        }

        if (tokenOverlapRatio(request, feature) >= MAX_ECHO_OVERLAP && featWords < MIN_NARRATIVE_WORDS + 10) {
            return new DepthResult(false, "Feature summary echoes the raw request without added substance (anti-echo).");
        }
        if (tokenOverlapRatio(request, exploration) >= MAX_ECHO_OVERLAP && exWords < MIN_EXPLORATION_WORDS + 15) {
            return new DepthResult(false, "Exploration mostly repeats the request (anti-echo).");
        }

        boolean workspaceReady = repoWorkspaceReady(plan);
        if (workspaceReady && !componentsLookCodeBacked(comps)) {
            return new DepthResult(
                    false,
                    "Under **Architecture — components affected**, list concrete file paths, packages, or extensions"
                            + " (the repo workspace is ready, so we expect code-backed touchpoints).");
        }

        if (!PlanningArtifactTexts.isReadyToImplementOpenQuestions(openQ) && !openQ.isBlank()
                && wordCount(openQ) < MIN_OPEN_QUESTIONS_WORDS) {
            return new DepthResult(false, "Open questions list is too short or template-like.");
        }
        if (!PlanningArtifactTexts.isReadyToImplementOpenQuestions(openQ)
                && !openQ.isBlank()
                && PlanningPlaceholderDetection.looksLikePlaceholder(openQ)) {
            return new DepthResult(false, "Open questions still look like starter template text.");
        }

        if (!validationLooksFeatureSpecific(validation, requestTokens, request)) {
            return new DepthResult(
                    false,
                    "Validation strategy must reference the feature or concrete paths, not only generic build commands.");
        }

        return new DepthResult(true, "Depth OK (exploration " + exWords + " words).");
    }

    /** Supplemental checks after declarative profile rules pass (workspace, open questions, validation specificity). */
    private static DepthResult supplementalAfterDeclarative(FeaturePlanState plan) {
        String request = plan.getInitialRequest() != null ? plan.getInitialRequest().trim() : "";
        Set<String> requestTokens = significantTokens(request);
        String comps = PlanningArtifactTexts.artifactField(plan, "architecture_notes", "impact", "components_impacted");
        String openQ = PlanningArtifactTexts.effectiveOpenQuestions(plan);
        String validation = PlanningArtifactTexts.artifactField(plan, "validation_plan", "checks", "validation_notes");
        String exploration = PlanningArtifactTexts.artifactField(plan, "request_exploration", "analysis", "exploration_body");
        int exWords = wordCount(exploration);

        boolean workspaceReady = repoWorkspaceReady(plan);
        if (workspaceReady && !componentsLookCodeBacked(comps)) {
            return new DepthResult(
                    false,
                    "Under **Architecture — components affected**, list concrete file paths, packages, or extensions"
                            + " (the repo workspace is ready, so we expect code-backed touchpoints).");
        }

        if (!PlanningArtifactTexts.isReadyToImplementOpenQuestions(openQ) && !openQ.isBlank()
                && wordCount(openQ) < MIN_OPEN_QUESTIONS_WORDS) {
            return new DepthResult(false, "Open questions list is too short or template-like.");
        }
        if (!PlanningArtifactTexts.isReadyToImplementOpenQuestions(openQ)
                && !openQ.isBlank()
                && PlanningPlaceholderDetection.looksLikePlaceholder(openQ)) {
            return new DepthResult(false, "Open questions still look like starter template text.");
        }

        if (!validationLooksFeatureSpecific(validation, requestTokens, request)) {
            return new DepthResult(
                    false,
                    "Validation strategy must reference the feature or concrete paths, not only generic build commands.");
        }

        return new DepthResult(true, "Depth OK (exploration " + exWords + " words).");
    }

    /** Public for {@link com.vinekeepers.workflow.actions.ExpandPlanningDraftsAction} to skip overwriting LLM expansion. */
    public static boolean componentsLookCodeBacked(String componentsImpacted) {
        if (componentsImpacted == null || componentsImpacted.isBlank()) {
            return false;
        }
        String c = componentsImpacted.toLowerCase(Locale.ROOT);
        return c.contains("src/")
                || c.contains(".java")
                || c.contains(".kt")
                || c.contains(".ts")
                || c.contains(".tsx")
                || c.contains(".py")
                || c.contains(".go")
                || c.contains("com.")
                || c.contains("package ")
                || c.contains("/");
    }

    private static boolean repoWorkspaceReady(FeaturePlanState plan) {
        String local = plan.getRepoLocalPath();
        return local != null
                && !local.isBlank()
                && Files.isDirectory(Path.of(local.trim()));
    }

    private static boolean validationLooksFeatureSpecific(String validation, Set<String> requestTokens, String request) {
        if (validation == null || validation.isBlank()) {
            return false;
        }
        String v = validation.toLowerCase(Locale.ROOT);
        if (PlanningPlaceholderDetection.looksLikePlaceholder(validation)) {
            return false;
        }
        if (wordCount(validation) >= 22) {
            return true;
        }
        if (v.contains("src/") || v.contains(".java") || v.contains(".ts") || v.contains("integration")) {
            return true;
        }
        for (String t : requestTokens) {
            if (t.length() >= 4 && v.contains(t)) {
                return true;
            }
        }
        Set<String> rt = significantTokens(request != null ? request : "");
        for (String t : rt) {
            if (t.length() >= 5 && v.contains(t)) {
                return true;
            }
        }
        boolean genericBoilerplate =
                v.contains("mvn test") && wordCount(validation) < 16 && !v.contains("feature") && !v.contains("request");
        return !genericBoilerplate;
    }

    public static int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private static double tokenOverlapRatio(String a, String b) {
        Set<String> sa = significantTokens(a);
        Set<String> sb = significantTokens(b);
        if (sa.isEmpty() || sb.isEmpty()) {
            return 0;
        }
        int inter = 0;
        for (String t : sa) {
            if (sb.contains(t)) {
                inter++;
            }
        }
        int denom = Math.min(sa.size(), sb.size());
        return denom == 0 ? 0 : (double) inter / denom;
    }

    private static Set<String> significantTokens(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) {
            return out;
        }
        var m = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String t = m.group();
            if (t.length() >= 4 && !STOPWORDS.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "that", "this", "with", "from", "have", "will", "your", "when", "what", "were", "been", "into", "than",
            "then", "them", "there", "these", "those", "about", "after", "before", "would", "could", "should"));
}
