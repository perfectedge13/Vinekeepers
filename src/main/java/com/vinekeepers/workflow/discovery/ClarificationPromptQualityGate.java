package com.vinekeepers.workflow.discovery;

import com.vinekeepers.state.planning.DiscoveryGap;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rejects placeholder-grade clarification copy before it reaches Discord. Intake planning uses canonical
 * {@link DiscoveryGap#getUserFacingDetail()} only; this gate blocks weak formatter fallbacks.
 */
public final class ClarificationPromptQualityGate {

    private static final Pattern FLESH_OUT_GENERIC =
            Pattern.compile("(?i)help us flesh out\\s*\\*\\*[^*]+\\*\\*\\s*with concrete detail\\.?");

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
        if (reason == null || reason.isBlank()) {
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
