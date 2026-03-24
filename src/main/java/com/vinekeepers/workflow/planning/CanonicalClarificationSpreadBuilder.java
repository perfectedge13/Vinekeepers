package com.vinekeepers.workflow.planning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.profile.CoordinatorClarificationGapRule;
import com.vinekeepers.profile.CoordinatorClarificationSettings;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Packages {@link CanonicalClarificationSelection} / {@link ClarificationProjection} for {@code canonical_v1} from
 * evaluation-backed {@link CanonicalPlanningGap} data. This class only shapes spread JSON and choice metadata.
 */
public final class CanonicalClarificationSpreadBuilder {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern OR_SPLIT = Pattern.compile("\\s+or\\s+", Pattern.CASE_INSENSITIVE);
    private static final int MIN_BOUNDED_OPTION_LEN = 5;
    private static final int MAX_BOUNDED_OPTION_LEN = 90;

    private CanonicalClarificationSpreadBuilder() {}

    /** Production adapter: uses {@link CanonicalPlanningGap#mergeTargetPath()} for merge metadata. */
    public static ClarificationProjection projectCanonicalPlanningGap(
            UnresolvedItemLedger ledger,
            WorkProfileDefinition profile,
            CoordinatorClarificationSettings coord,
            CanonicalPlanningGap top,
            String resolvedQuestionText,
            List<String> carryAssumptions,
            QuestionMode questionMode) {
        if (top == null) {
            return ClarificationProjection.fromSelection(CanonicalClarificationSelection.none());
        }
        String mergePath = top.mergeTargetPath();
        if (mergePath == null || mergePath.isBlank()) {
            return ClarificationProjection.fromSelection(CanonicalClarificationSelection.none());
        }
        return ClarificationProjection.fromSelection(
                buildSelectionForAsk(
                        profile,
                        coord,
                        top.gapId(),
                        top.blocking(),
                        resolvedQuestionText,
                        carryAssumptions,
                        questionMode,
                        mergePath));
    }

    private static CanonicalClarificationSelection buildSelectionForAsk(
            WorkProfileDefinition profile,
            CoordinatorClarificationSettings coord,
            String gapId,
            boolean blocking,
            String resolvedQuestionText,
            List<String> carryAssumptions,
            QuestionMode questionMode,
            String mergeTargetPath) {
        List<String> assumptions = carryAssumptions != null ? new ArrayList<>(carryAssumptions) : new ArrayList<>();
        String gid = gapId != null ? gapId.trim() : "";
        String q = resolvedQuestionText != null ? resolvedQuestionText.trim() : "";
        if (q.length() > 240) {
            q = q.substring(0, 239) + "…";
        }
        if (q.isBlank() || mergeTargetPath == null || mergeTargetPath.isBlank()) {
            return new CanonicalClarificationSelection(
                    false, gid, "", "", questionMode, false, false, "", "[]", "{}", assumptions);
        }
        CoordinatorClarificationGapRule rule = coord != null ? coord.findGapRule(gid).orElse(null) : null;
        boolean bounded =
                rule != null
                        && rule.isUseBoundedChoiceUi()
                        && profile != null
                        && profile.isBoundedClarificationChoicesEnabled();
        boolean inferOr = bounded && rule != null && rule.isInferOrChoices();
        ClarificationOptions boundedOpts = bounded && inferOr ? inferBoundedOrOptions(q) : null;
        String mergePath = mergeTargetPath.trim();
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("questionText", q);
            meta.put("gapId", gid);
            meta.put("mergeTargetPath", mergePath);
            meta.put("questionMode", questionMode.name());
            if (boundedOpts != null) {
                meta.put("defaultAssumption", boundedOpts.defaultAssumption());
                meta.put("optA", boundedOpts.optA());
                meta.put("optB", boundedOpts.optB());
                meta.put("optC", boundedOpts.optC());
                String metaJson = JSON.writeValueAsString(meta);
                List<Map<String, String>> choiceMaps = new ArrayList<>();
                choiceMaps.add(
                        Map.of(
                                "id",
                                "planning_clarify_default",
                                "label",
                                "Use recommended default",
                                "description",
                                "Record the default below and continue."));
                for (int i = 0; i < boundedOpts.labels().size(); i++) {
                    String id =
                            i == 0
                                    ? "planning_clarify_opt_a"
                                    : (i == 1 ? "planning_clarify_opt_b" : "planning_clarify_opt_c");
                    choiceMaps.add(
                            Map.of(
                                    "id",
                                    id,
                                    "label",
                                    truncate(boundedOpts.labels().get(i), 72),
                                    "description",
                                    ""));
                }
                String choicesJson = JSON.writeValueAsString(choiceMaps);
                String prompt = "**" + q + "**\n\nPick an option below, or **Use recommended default** if that fits.";
                return new CanonicalClarificationSelection(
                        true,
                        gid,
                        mergePath,
                        q,
                        questionMode,
                        blocking,
                        true,
                        prompt,
                        choicesJson,
                        metaJson,
                        assumptions);
            }
            meta.put("defaultAssumption", "");
            meta.put("optA", "");
            meta.put("optB", "");
            meta.put("optC", "");
            String metaJson = JSON.writeValueAsString(meta);
            return new CanonicalClarificationSelection(
                    true,
                    gid,
                    mergePath,
                    q,
                    questionMode,
                    blocking,
                    false,
                    "",
                    "[]",
                    metaJson,
                    assumptions);
        } catch (JsonProcessingException e) {
            return new CanonicalClarificationSelection(
                    false, gid, "", "", questionMode, false, false, "", "[]", "{}", assumptions);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private record ClarificationOptions(
            List<String> labels, String defaultAssumption, String optA, String optB, String optC) {}

    private static ClarificationOptions inferBoundedOrOptions(String question) {
        String[] parts = OR_SPLIT.split(question, 3);
        if (parts.length < 2) {
            return null;
        }
        String rightRaw = parts[1] != null ? parts[1].trim() : "";
        if (parts.length >= 3 && parts[2] != null && !parts[2].isBlank()) {
            return null;
        }
        if ((parts[0] != null && parts[0].contains(",")) || rightRaw.contains(",")) {
            return null;
        }
        String a = cleanOption(parts[0]);
        String b = cleanOption(parts[1]);
        if (a.length() < MIN_BOUNDED_OPTION_LEN
                || b.length() < MIN_BOUNDED_OPTION_LEN
                || a.length() > MAX_BOUNDED_OPTION_LEN
                || b.length() > MAX_BOUNDED_OPTION_LEN) {
            return null;
        }
        String def = "Proceed with " + a + " unless product guidance prefers " + b + ".";
        return new ClarificationOptions(List.of(a, b), def, a, b, "");
    }

    private static String cleanOption(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.replaceFirst("(?i)^(should|must|will|can)\\s+", "").trim();
        t = t.replaceFirst("\\?$", "").trim();
        return truncate(t, 80);
    }
}
