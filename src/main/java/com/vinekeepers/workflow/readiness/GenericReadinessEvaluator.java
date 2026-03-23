package com.vinekeepers.workflow.readiness;

import com.vinekeepers.profile.ArtifactDefinition;
import com.vinekeepers.profile.FieldDefinition;
import com.vinekeepers.profile.ReadinessAnyOfGroup;
import com.vinekeepers.profile.ReadinessPathRule;
import com.vinekeepers.profile.SectionDefinition;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.workflow.planning.PlanningPlaceholderDetection;
import com.vinekeepers.workflow.planreview.PlanningArtifactTexts;
import com.vinekeepers.workflow.planreview.PlanningPacketDepthEvaluator;
import com.vinekeepers.workflow.planreview.PlanningUserFacingCopy;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Evaluates declarative readiness rules from {@link WorkProfileDefinition} (field constraints + anyOf groups).
 */
public final class GenericReadinessEvaluator {

    private static final Pattern TOKEN = Pattern.compile("\\w+", Pattern.UNICODE_CHARACTER_CLASS);

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "that", "this", "with", "from", "have", "will", "your", "when", "what", "were", "been", "into", "than",
            "then", "them", "there", "these", "those", "about", "after", "before", "would", "could", "should"));

    private GenericReadinessEvaluator() {}

    /**
     * @return depth-style result; when ok is false, reason is user-facing.
     */
    public static PlanningPacketDepthEvaluator.DepthResult evaluate(WorkProfileDefinition profile, FeaturePlanState plan) {
        if (plan == null) {
            return new PlanningPacketDepthEvaluator.DepthResult(false, "No plan loaded.");
        }
        if (profile == null || !profile.hasDeclarativeReadiness()) {
            return new PlanningPacketDepthEvaluator.DepthResult(true, "No declarative readiness configured.");
        }
        String request = plan.getInitialRequest() != null ? plan.getInitialRequest().trim() : "";

        for (ReadinessAnyOfGroup group : profile.getReadinessAnyOfGroups()) {
            boolean any = false;
            for (ReadinessPathRule rule : group.getRules()) {
                PlanningPacketDepthEvaluator.DepthResult one = checkPathRule(profile, plan, request, rule);
                if (one.ok()) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return new PlanningPacketDepthEvaluator.DepthResult(
                        false,
                        "At least one readiness group still needs stronger detail—expand request exploration or narrative"
                                + " sections so each group has at least one field that meets the word-count rules.");
            }
        }

        for (ArtifactDefinition art : profile.getArtifactsById().values()) {
            for (SectionDefinition sec : art.getSections()) {
                for (FieldDefinition field : sec.getFields()) {
                    if (!field.hasReadinessConstraints()) {
                        continue;
                    }
                    ReadinessPathRule synthetic = new ReadinessPathRule(
                            art.getArtifactId(),
                            sec.getSectionId(),
                            field.getFieldId(),
                            field.getMinWords(),
                            field.getMaxEchoOverlapWithRequest(),
                            field.getEchoWordSlack(),
                            field.isSkipReadinessIfBlank(),
                            field.getReadinessChecks());
                    PlanningPacketDepthEvaluator.DepthResult r = checkPathRule(profile, plan, request, synthetic);
                    if (!r.ok()) {
                        return r;
                    }
                }
            }
        }

        PlanningPacketDepthEvaluator.DepthResult forbidden = checkPacketQualityForbiddenSubstrings(profile, plan);
        if (!forbidden.ok()) {
            return forbidden;
        }

        return new PlanningPacketDepthEvaluator.DepthResult(true, "Declarative readiness OK.");
    }

    private static PlanningPacketDepthEvaluator.DepthResult checkPacketQualityForbiddenSubstrings(
            WorkProfileDefinition profile, FeaturePlanState plan) {
        if (profile == null || plan == null) {
            return new PlanningPacketDepthEvaluator.DepthResult(true, "skip");
        }
        var forbidden = profile.getPacketQualityForbiddenSubstrings();
        if (forbidden == null || forbidden.isEmpty()) {
            return new PlanningPacketDepthEvaluator.DepthResult(true, "skip");
        }
        for (ArtifactDefinition art : profile.getArtifactsById().values()) {
            for (SectionDefinition sec : art.getSections()) {
                for (FieldDefinition field : sec.getFields()) {
                    String text =
                            PlanningArtifactTexts.artifactField(
                                    plan, art.getArtifactId(), sec.getSectionId(), field.getFieldId());
                    if (text == null || text.isBlank()) {
                        continue;
                    }
                    String low = text.toLowerCase(Locale.ROOT);
                    for (String pat : forbidden) {
                        if (pat != null
                                && !pat.isBlank()
                                && low.contains(pat.toLowerCase(Locale.ROOT).trim())) {
                            String label =
                                    PlanningUserFacingCopy.describePlanningFieldPath(
                                            profile,
                                            art.getArtifactId(),
                                            sec.getSectionId(),
                                            field.getFieldId());
                            return new PlanningPacketDepthEvaluator.DepthResult(
                                    false,
                                    (label.isBlank() ? "A planning field" : label)
                                            + " still contains disallowed placeholder wording from the work profile.");
                        }
                    }
                }
            }
        }
        return new PlanningPacketDepthEvaluator.DepthResult(true, "Packet substring rules OK.");
    }

    private static PlanningPacketDepthEvaluator.DepthResult checkPathRule(
            WorkProfileDefinition profile, FeaturePlanState plan, String request, ReadinessPathRule rule) {
        String text = PlanningArtifactTexts.artifactField(plan, rule.getArtifactId(), rule.getSectionId(), rule.getFieldId());
        if (text == null) {
            text = "";
        }
        text = text.trim();
        if (rule.isSkipIfBlank() && text.isBlank()) {
            return new PlanningPacketDepthEvaluator.DepthResult(true, "skip blank");
        }

        int words = PlanningPacketDepthEvaluator.wordCount(text);
        if (rule.getMinWords() != null && words < rule.getMinWords()) {
            String label =
                    PlanningUserFacingCopy.describePlanningFieldPath(
                            profile, rule.getArtifactId(), rule.getSectionId(), rule.getFieldId());
            return new PlanningPacketDepthEvaluator.DepthResult(
                    false,
                    (label.isBlank() ? "A planning field" : label)
                            + " is too thin ("
                            + words
                            + " words; need at least "
                            + rule.getMinWords()
                            + ").");
        }

        for (String check : rule.getReadinessChecks()) {
            String c = check != null ? check.trim().toLowerCase(Locale.ROOT) : "";
            if ("placeholder".equals(c)) {
                if (PlanningPlaceholderDetection.looksLikePlaceholder(text)) {
                    String label =
                            PlanningUserFacingCopy.describePlanningFieldPath(
                                    profile, rule.getArtifactId(), rule.getSectionId(), rule.getFieldId());
                    return new PlanningPacketDepthEvaluator.DepthResult(
                            false,
                            (label.isBlank() ? "A planning field" : label)
                                    + " still uses hollow or placeholder phrasing.");
                }
            } else if ("hollow_exploration".equals(c)) {
                if (PlanningPlaceholderDetection.looksLikeHollowExploration(text)) {
                    return new PlanningPacketDepthEvaluator.DepthResult(
                            false, "Request exploration reads as placeholder or template.");
                }
            }
        }

        if (rule.getMaxEchoOverlapWithRequest() != null) {
            double overlap = tokenOverlapRatio(request, text);
            int slack = rule.getEchoWordSlack() != null ? rule.getEchoWordSlack() : 10;
            int baseMin = rule.getMinWords() != null ? rule.getMinWords() : 0;
            if (overlap >= rule.getMaxEchoOverlapWithRequest() && words < baseMin + slack) {
                String label =
                        PlanningUserFacingCopy.describePlanningFieldPath(
                                profile, rule.getArtifactId(), rule.getSectionId(), rule.getFieldId());
                return new PlanningPacketDepthEvaluator.DepthResult(
                        false,
                        (label.isBlank() ? "A planning field" : label)
                                + " echoes the raw request without enough added substance.");
            }
        }

        return new PlanningPacketDepthEvaluator.DepthResult(true, "ok");
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
}
