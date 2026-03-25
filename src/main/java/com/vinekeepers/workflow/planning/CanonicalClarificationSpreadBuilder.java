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
    private static final int MIN_BOUNDED_OPTION_LEN = 4;
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
        ClarificationOptions boundedOpts =
                inferDecisionForkOptions(q, bounded && inferOr ? inferBoundedOrOptions(q) : null);
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
                                "Not sure / choose default",
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
                String prompt = "**" + q + "**\n\nPick an option below, or choose the default path if you are not sure.";
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

    private static ClarificationOptions inferDecisionForkOptions(String question, ClarificationOptions optInOptions) {
        if (optInOptions != null) {
            return optInOptions;
        }
        ClarificationOptions threeWay = inferThreeWayOptions(question);
        if (threeWay != null) {
            return threeWay;
        }
        return inferBoundedOrOptions(question);
    }

    private static ClarificationOptions inferThreeWayOptions(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }
        String q = question.trim().replaceFirst("\\?$", "");
        int lastOr = q.toLowerCase().lastIndexOf(" or ");
        if (lastOr < 0) {
            return null;
        }
        String left = q.substring(0, lastOr).trim();
        String thirdRaw = q.substring(lastOr + 4).trim();
        int comma = left.lastIndexOf(',');
        if (comma < 0) {
            return null;
        }
        String middleRaw = left.substring(comma + 1).trim();
        String firstRaw = left.substring(0, comma).trim();
        int firstComma = firstRaw.lastIndexOf(',');
        if (firstComma >= 0) {
            middleRaw = firstRaw.substring(firstComma + 1).trim();
            firstRaw = firstRaw.substring(0, firstComma).trim();
        }
        String a = cleanOption(firstRaw);
        String b = cleanOption(middleRaw);
        String c = cleanOption(thirdRaw);
        if (!validOption(a) || !validOption(b) || !validOption(c)) {
            return null;
        }
        String def = "Proceed with " + a + " unless product guidance prefers " + b + " or " + c + ".";
        return new ClarificationOptions(List.of(a, b, c), def, a, b, c);
    }

    private static ClarificationOptions inferBoundedOrOptions(String question) {
        String[] parts = OR_SPLIT.split(question, 3);
        if (parts.length < 2) {
            return null;
        }
        if (parts.length >= 3 && parts[2] != null && !parts[2].isBlank()) {
            return null;
        }
        String a = cleanOption(parts[0]);
        String b = cleanOption(parts[1]);
        if (!validOption(a) || !validOption(b)) {
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
        t = stripLeadingDecisionStem(t);
        t = t.replaceAll("^[,\\s]+", "").trim();
        t = t.replaceAll("[,\\s]+$", "").trim();
        t = t.replaceFirst("\\?$", "").trim();
        return truncate(t, 80);
    }

    private static String stripLeadingDecisionStem(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String lowered = value.toLowerCase();
        for (String token : List.of(" live in ", " belong in ", " be in ", " be configured in ", " be stored in ")) {
            int idx = lowered.lastIndexOf(token);
            if (idx >= 0) {
                return value.substring(idx + token.length()).trim();
            }
        }
        return value;
    }

    private static boolean validOption(String value) {
        return value.length() >= MIN_BOUNDED_OPTION_LEN && value.length() <= MAX_BOUNDED_OPTION_LEN;
    }
}
