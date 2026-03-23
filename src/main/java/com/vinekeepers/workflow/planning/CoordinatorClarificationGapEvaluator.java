package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.CoordinatorClarificationGapRule;
import com.vinekeepers.profile.CoordinatorClarificationSettings;
import com.vinekeepers.state.planning.PlanAssumption;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.workflow.discovery.ClarificationPromptQualityGate;
import com.vinekeepers.workflow.planreview.PlanningArtifactTexts;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Derives open coordinator clarification gaps from canonical plan text and profile rules. LLM follow-up strings only
 * trigger gaps when they match configured {@code hintDetectAllOf} patterns — they are not themselves the source of
 * truth for whether a gap remains.
 */
public final class CoordinatorClarificationGapEvaluator {

    public record OpenGap(String gapId, boolean blocking, String questionText) {}

    private CoordinatorClarificationGapEvaluator() {}

    /**
     * Returns ordered open gaps (at most one is typically surfaced per cycle). Empty when mode is not canonical or no
     * rules fire.
     */
    public static List<OpenGap> evaluateOpenGaps(
            FeaturePlanState plan, CoordinatorClarificationSettings settings, List<String> llmHints) {
        return evaluateOpenGaps(plan, settings, llmHints, true);
    }

    /**
     * @param semanticGapsAllowed when false, returns no open gaps (hard / workspace-only path is handled outside this
     *     evaluator; post-draft clarification sets this true once drafting has run).
     */
    public static List<OpenGap> evaluateOpenGaps(
            FeaturePlanState plan,
            CoordinatorClarificationSettings settings,
            List<String> llmHints,
            boolean semanticGapsAllowed) {
        if (plan == null || settings == null || !settings.isCanonicalV1() || !semanticGapsAllowed) {
            return List.of();
        }
        String canonical = buildCanonicalResolutionText(plan);
        List<String> hints = llmHints != null ? llmHints : List.of();
        List<OpenGap> out = new ArrayList<>();
        for (CoordinatorClarificationGapRule rule : settings.getGaps()) {
            if (isResolved(canonical, rule)) {
                continue;
            }
            if (!isTriggered(canonical, hints, rule)) {
                continue;
            }
            String q = rule.getQuestionTemplate();
            if (q.isBlank() || ClarificationPromptQualityGate.isGenericOrMetaClarification(q)) {
                continue;
            }
            out.add(new OpenGap(rule.getId(), rule.isBlocking(), q.trim()));
        }
        return List.copyOf(out);
    }

    static String buildCanonicalResolutionText(FeaturePlanState plan) {
        if (plan == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String req = plan.getInitialRequest();
        if (req != null && !req.isBlank()) {
            sb.append(req.trim()).append('\n');
        }
        for (PlanAssumption a : plan.getAssumptions()) {
            if (a.getText() != null && !a.getText().isBlank()) {
                sb.append(a.getText().trim()).append('\n');
            }
        }
        sb.append(
                PlanningArtifactTexts.artifactField(plan, "request_exploration", "analysis", "exploration_body"));
        sb.append('\n');
        sb.append(PlanningArtifactTexts.allRepeatableFieldLines(plan, "decision_log", "decisions", "decision_text"));
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    static boolean isResolved(String canonicalLower, CoordinatorClarificationGapRule rule) {
        if (canonicalLower == null || canonicalLower.isBlank()) {
            return false;
        }
        for (String needle : rule.getResolveAnySubstring()) {
            if (needle == null || needle.isBlank()) {
                continue;
            }
            if (canonicalLower.contains(needle.toLowerCase(Locale.ROOT).trim())) {
                return true;
            }
        }
        return false;
    }

    static boolean isTriggered(String canonicalLower, List<String> hints, CoordinatorClarificationGapRule rule) {
        boolean hintOk = hintMatches(hints, rule);
        boolean canonOk = canonicalOpenMatches(canonicalLower, rule);
        return hintOk || canonOk;
    }

    private static boolean hintMatches(List<String> hints, CoordinatorClarificationGapRule rule) {
        List<String> allOf = rule.getHintDetectAllOf();
        if (allOf.isEmpty()) {
            return false;
        }
        for (String hintLine : hints) {
            if (hintLine == null || hintLine.isBlank()) {
                continue;
            }
            String h = hintLine.toLowerCase(Locale.ROOT);
            boolean ok = true;
            for (String part : allOf) {
                if (part == null || part.isBlank()) {
                    continue;
                }
                if (!h.contains(part.toLowerCase(Locale.ROOT).trim())) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return true;
            }
        }
        return false;
    }

    private static boolean canonicalOpenMatches(String canonicalLower, CoordinatorClarificationGapRule rule) {
        List<String> allOf = rule.getCanonicalOpenAllOf();
        if (allOf.isEmpty()) {
            return false;
        }
        for (String part : allOf) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!canonicalLower.contains(part.toLowerCase(Locale.ROOT).trim())) {
                return false;
            }
        }
        return true;
    }

    /** Gap ids still considered open (for ledger reconciliation). */
    public static Set<String> openGapIds(List<OpenGap> open) {
        if (open == null || open.isEmpty()) {
            return Set.of();
        }
        Set<String> s = new LinkedHashSet<>();
        for (OpenGap g : open) {
            if (g.gapId() != null && !g.gapId().isBlank()) {
                s.add(g.gapId().trim());
            }
        }
        return s;
    }
}
