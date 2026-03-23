package com.vinekeepers.workflow.discovery;

import com.vinekeepers.state.planning.DiscoveryGap;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rejects placeholder-grade clarification copy before it reaches Discord. Intake planning uses canonical
 * {@link DiscoveryGap#getUserFacingDetail()} only; this gate blocks weak formatter fallbacks.
 *
 * <p>Also enforces the Arrietty clarification contract: no generic completeness / meta “open questions”
 * boilerplate; prompts must be concrete asks tied to a gap or ranked candidate.
 */
public final class ClarificationPromptQualityGate {

    private static final Pattern FLESH_OUT_GENERIC =
            Pattern.compile("(?i)help us flesh out\\s*\\*\\*[^*]+\\*\\*\\s*with concrete detail\\.?");

    private static final Pattern ANYTHING_ELSE_Q =
            Pattern.compile("(?i)\\b(anything|something)\\s+else\\b.*\\?");

    private static final Set<String> WEAK_STANDALONE_LABELS = Set.of(
            "decision",
            "scope",
            "details",
            "detail",
            "summary",
            "plan",
            "context",
            "this item",
            "item");

    private ClarificationPromptQualityGate() {}

    /**
     * True for stack traces, parse errors, and other pipeline internals that must not appear in Discord clarification copy.
     */
    public static boolean isInternalMechanismDetail(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String t = text.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.contains("at com.") || lower.contains("at java.")) {
            return true;
        }
        if (lower.contains("exception") || lower.contains("stacktrace") || lower.contains("stack trace")) {
            return true;
        }
        if (t.startsWith("ERROR:") || t.startsWith("error:")) {
            return true;
        }
        if (lower.contains("json") && (lower.contains("parse") || lower.contains("unexpected token"))) {
            return true;
        }
        if (t.startsWith("{") && t.contains("}")) {
            return true;
        }
        return false;
    }

    /**
     * True when a line is concrete enough to list as an open question in the planning packet (governance / unresolved state).
     */
    public static boolean isSubstantiveOpenQuestionLine(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String t = text.trim();
        if (t.length() < 12) {
            return false;
        }
        if (isGenericOrMetaClarification(t)) {
            return false;
        }
        return !isInternalMechanismDetail(t);
    }

    /**
     * True when text is generic completeness / meta phrasing that must not be asked as a blocking clarification
     * (LLM follow-ups, artifact lines, packet copy).
     */
    public static boolean isGenericOrMetaClarification(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String t = text.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.contains("open questions")) {
            return true;
        }
        if (lower.contains("open question") && lower.contains("list")) {
            return true;
        }
        if (lower.contains("any other questions") || lower.contains("any other open")) {
            return true;
        }
        if (lower.contains("are there any") && lower.contains("?")) {
            return true;
        }
        if (lower.contains("is there anything") && lower.contains("?")) {
            return true;
        }
        if (lower.contains("what else should") || lower.contains("what other areas")) {
            return true;
        }
        if (lower.contains("completeness") || lower.contains("complete the plan")) {
            return true;
        }
        if (lower.contains("missing anything") || lower.contains("anything we're missing")) {
            return true;
        }
        if (lower.contains("before we proceed") && lower.contains("clarif")) {
            return true;
        }
        if (Pattern.compile("(?i)\\b(do you need any|any further)\\s+clarification\\b").matcher(t).find()) {
            return true;
        }
        if (ANYTHING_ELSE_Q.matcher(t).find()) {
            return true;
        }
        if (lower.contains("review the") && lower.contains("open items")) {
            return true;
        }
        return false;
    }

    /**
     * Minimum bar for a ranked LLM follow-up to become a clarification candidate (shorter than
     * {@link #passes(String)} allows for final Discord copy).
     */
    public static boolean acceptableClarificationCandidate(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String t = text.trim();
        if (t.length() < 12) {
            return false;
        }
        return !isGenericOrMetaClarification(t);
    }

    /**
     * Returns a single user-safe prompt, or empty if no acceptable question can be formed (caller should skip
     * posting and continue drafting).
     */
    public static String sanitizeBlockingQuestion(String candidate, DiscoveryGap gap) {
        if (candidate != null && passes(candidate)) {
            return candidate.trim();
        }
        if (gap != null) {
            String detail = gap.getUserFacingDetail();
            if (detail != null && !detail.isBlank() && passes(detail)) {
                return detail.trim();
            }
            String fallback = synthesizeFromGap(gap);
            if (fallback != null && !fallback.isBlank() && passes(fallback)) {
                return fallback.trim();
            }
        }
        return "";
    }

    static boolean passes(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String t = text.trim();
        if (t.length() < 24) {
            return false;
        }
        if (isGenericOrMetaClarification(t)) {
            return false;
        }
        if (FLESH_OUT_GENERIC.matcher(t).find()) {
            return false;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.contains("planning check-in")) {
            return false;
        }
        if (lower.startsWith("reply with") && lower.length() < 48) {
            return false;
        }
        // Standalone weak title lines
        String stripped = t.replace("*", "").trim().toLowerCase(Locale.ROOT);
        if (WEAK_STANDALONE_LABELS.contains(stripped)) {
            return false;
        }
        return true;
    }

    private static String synthesizeFromGap(DiscoveryGap gap) {
        if (gap == null) {
            return "";
        }
        String kind = gap.getKind() != null ? gap.getKind() : "";
        if ("WORKSPACE".equalsIgnoreCase(kind)) {
            String d = gap.getUserFacingDetail();
            if (d != null && !d.isBlank()) {
                return d.trim();
            }
        }
        String reason = gap.getReason();
        if (reason == null || reason.isBlank() || isInternalMechanismDetail(reason)) {
            return "";
        }
        String r = reason.trim();
        if (r.length() > 280) {
            r = r.substring(0, 277) + "…";
        }
        return "Before we can finalize the plan, we need one thing: "
                + r
                + " Reply in plain text in this thread.";
    }
}
