package com.vinekeepers.workflow.planning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.workflow.discovery.ClarificationPromptQualityGate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Ranks LLM/critique clarification candidates, applies safe defaults as assumptions instead of asking,
 * and prepares at most one clarification round per cycle ({@code maxQuestions} is typically {@code 1} so only the first
 * qualifying concrete ask is considered). Open questions default to plain-text capture;
 * structured buttons are used only when the work profile enables bounded UI and the question has an explicit
 * {@code or}-separated alternative pair.
 */
public final class PlanningQuestionRankingPolicy {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern OR_SPLIT = Pattern.compile("\\s+or\\s+", Pattern.CASE_INSENSITIVE);
    /** Minimum cleaned length for each side of an {@code or} split to treat as bounded choices. */
    private static final int MIN_BOUNDED_OPTION_LEN = 5;
    private static final int MAX_BOUNDED_OPTION_LEN = 90;

    private PlanningQuestionRankingPolicy() {}

    public record RankedClarification(
            boolean userInputRequired,
            /** Prompt body when using structured choices (buttons); empty for open-text mode. */
            String orchestratorPrompt,
            String choicesJson,
            String metaJson,
            int blockingQuestionCount,
            List<String> assumptionsToRecord,
            /** When true, YAML should use {@code present_choices} + {@code planningClarification}. */
            boolean useStructuredChoices,
            /** Canonical question text for meta merge and summaries. */
            String questionText) {}

    /**
     * Same as {@link #rank(FeaturePlanState, List, int, UnresolvedItemLedger, boolean)} with an empty ledger and
     * bounded choice UI disabled (plain text by default).
     */
    public static RankedClarification rank(
            FeaturePlanState plan,
            List<String> candidates,
            int maxQuestions) {
        return rank(plan, candidates, maxQuestions, UnresolvedItemLedger.empty(), false, false);
    }

    /**
     * Same as {@link #rank(FeaturePlanState, List, int, UnresolvedItemLedger, boolean, boolean)} with
     * {@code applyOrTextHeuristic == allowBoundedChoiceUi} (legacy tests / callers).
     */
    public static RankedClarification rank(
            FeaturePlanState plan,
            List<String> candidates,
            int maxQuestions,
            UnresolvedItemLedger ledger,
            boolean allowBoundedChoiceUi) {
        return rank(plan, candidates, maxQuestions, ledger, allowBoundedChoiceUi, allowBoundedChoiceUi);
    }

    /**
     * @param candidates   raw questions from LLM passes (deduped)
     * @param maxQuestions budget for how many ranked candidates to collect before taking the first (use {@code 1} for strict
     *     single-question coordinator contract)
     * @param ledger       items with merge-closed fingerprints are skipped so resolved questions are not re-asked
     * @param allowBoundedChoiceUi when false, never emit button/dropdown clarification from the OR heuristic path
     * @param applyOrTextHeuristic when false, never infer A/B buttons from {@code or} in free text (even if bounded UI is
     *     enabled for other paths)
     */
    public static RankedClarification rank(
            FeaturePlanState plan,
            List<String> candidates,
            int maxQuestions,
            UnresolvedItemLedger ledger,
            boolean allowBoundedChoiceUi,
            boolean applyOrTextHeuristic) {
        UnresolvedItemLedger led = ledger != null ? ledger : UnresolvedItemLedger.empty();
        List<String> assumptions = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        int blocking = 0;
        for (String raw : dedupe(candidates)) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String q = raw.trim();
            if (q.length() > 240) {
                q = q.substring(0, 239) + "…";
            }
            if (!ClarificationPromptQualityGate.acceptableClarificationCandidate(q)) {
                continue;
            }
            if (led.hasFingerprintMergeClosed(q)) {
                continue;
            }
            DefaultResolution d = tryResolveWithDefault(q);
            if (d != null) {
                assumptions.add(d.assumptionText());
                continue;
            }
            int score = scoreQuestion(q);
            if (score < 4) {
                continue;
            }
            pending.add(q);
            if (isBlocking(q)) {
                blocking++;
            }
            if (pending.size() >= maxQuestions) {
                break;
            }
        }
        if (pending.isEmpty()) {
            return new RankedClarification(false, "", "[]", "{}", 0, assumptions, false, "");
        }
        String top = pending.get(0);
        ClarificationOptions bounded =
                allowBoundedChoiceUi && applyOrTextHeuristic ? inferBoundedOrOptions(top) : null;
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("questionText", top);
            if (bounded != null) {
                meta.put("defaultAssumption", bounded.defaultAssumption());
                meta.put("optA", bounded.optA());
                meta.put("optB", bounded.optB());
                meta.put("optC", bounded.optC());
                String metaJson = JSON.writeValueAsString(meta);
                List<Map<String, String>> choiceMaps = new ArrayList<>();
                choiceMaps.add(Map.of(
                        "id",
                        "planning_clarify_default",
                        "label",
                        "Use recommended default",
                        "description",
                        "Record the default below and continue."));
                for (int i = 0; i < bounded.labels().size(); i++) {
                    String id = i == 0 ? "planning_clarify_opt_a" : (i == 1 ? "planning_clarify_opt_b" : "planning_clarify_opt_c");
                    choiceMaps.add(Map.of("id", id, "label", truncate(bounded.labels().get(i), 72), "description", ""));
                }
                String choicesJson = JSON.writeValueAsString(choiceMaps);
                String prompt = "**" + top + "**\n\nPick an option below, or **Use recommended default** if that fits.";
                return new RankedClarification(true, prompt, choicesJson, metaJson, blocking, assumptions, true, top);
            }
            meta.put("defaultAssumption", "");
            meta.put("optA", "");
            meta.put("optB", "");
            meta.put("optC", "");
            String metaJson = JSON.writeValueAsString(meta);
            return new RankedClarification(true, "", "[]", metaJson, blocking, assumptions, false, top);
        } catch (JsonProcessingException e) {
            return new RankedClarification(false, "", "[]", "{}", 0, assumptions, false, "");
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static List<String> dedupe(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) {
            if (s == null || s.isBlank()) {
                continue;
            }
            String t = s.trim();
            boolean dup = false;
            for (String e : out) {
                if (similarity(e, t) > 0.72) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                out.add(t);
            }
        }
        return out;
    }

    /** Similarity in [0,1] for paraphrase / duplicate detection (ledger gap evaluation). */
    public static double clarificationSimilarity(String a, String b) {
        return similarity(a, b);
    }

    private static double similarity(String a, String b) {
        if (a == null || b == null) {
            return 0;
        }
        String x = a.toLowerCase(Locale.ROOT);
        String y = b.toLowerCase(Locale.ROOT);
        if (x.equals(y)) {
            return 1;
        }
        long common = x.chars().filter(ch -> y.indexOf(ch) >= 0).count();
        return (2.0 * common) / (x.length() + y.length() + 1);
    }

    private static boolean isBlocking(String q) {
        String s = q.toLowerCase(Locale.ROOT);
        return s.contains("breaking")
                || s.contains("compat")
                || s.contains("migration")
                || s.contains("security")
                || s.contains("schema")
                || s.contains("validation") && s.contains("must");
    }

    private static int scoreQuestion(String q) {
        String s = q.toLowerCase(Locale.ROOT);
        int score = 0;
        if (s.contains("implement") || s.contains("runtime") || s.contains("config")) {
            score += 3;
        }
        if (s.contains("architecture") || s.contains("component") || s.contains("workflow") || s.contains("override")) {
            score += 3;
        }
        if (s.contains("valid") || s.contains("test") || s.contains("migrat") || s.contains("backward")) {
            score += 2;
        }
        if (s.contains("should ") || s.contains("must we") || s.contains("do we need")) {
            score += 2;
        }
        if (s.contains("favorite color") || s.contains("what would you like") || s.length() < 12) {
            score -= 5;
        }
        return score;
    }

    private record DefaultResolution(String assumptionText) {}

    private static DefaultResolution tryResolveWithDefault(String q) {
        String s = q.toLowerCase(Locale.ROOT);
        if (s.contains("override") && (s.contains("provider") || s.contains("model"))) {
            return new DefaultResolution(
                    "Default: workflow YAML exposes a global LLM default (provider/model); each LLM-capable step may override model and/or provider; non-LLM steps do not declare LLM fields.");
        }
        if (s.contains("invalid") && s.contains("override")) {
            return new DefaultResolution(
                    "Default: invalid LLM overrides on a step fail workflow validation (no silent fallback to the workflow default).");
        }
        if (s.contains("config") && s.contains("runtime") && s.contains("only")) {
            return new DefaultResolution(
                    "Default: assume both configuration schema and runtime resolution behavior may change unless the request explicitly limits scope to config-only.");
        }
        if (s.contains("non-llm") && s.contains("llm")) {
            return new DefaultResolution("Default: non-LLM workflow steps cannot define LLM overrides.");
        }
        return null;
    }

    private record ClarificationOptions(
            List<String> labels, String defaultAssumption, String optA, String optB, String optC) {}

    /**
     * @return bounded options only for explicit {@code A or B} style questions with two substantive clauses; otherwise null (open text).
     *     Rhetorical clarifications often contain {@code , or } or a third clause after a second {@code or}; those stay open-text.
     */
    private static ClarificationOptions inferBoundedOrOptions(String question) {
        String[] parts = OR_SPLIT.split(question, 3);
        if (parts.length < 2) {
            return null;
        }
        String leftRaw = parts[0] != null ? parts[0].trim() : "";
        String rightRaw = parts[1] != null ? parts[1].trim() : "";
        if (parts.length >= 3 && parts[2] != null && !parts[2].isBlank()) {
            return null;
        }
        if (leftRaw.contains(",") || rightRaw.contains(",")) {
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
