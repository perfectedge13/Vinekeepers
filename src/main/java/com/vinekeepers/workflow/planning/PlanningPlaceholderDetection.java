package com.vinekeepers.workflow.planning;

/**
 * Detects seeded / generic placeholder text that should not count as a completed planning packet.
 */
public final class PlanningPlaceholderDetection {

    private PlanningPlaceholderDetection() {}

    public static boolean looksLikePlaceholder(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String v = value.trim().toLowerCase();
        if (v.equals("ok") || v.equals("yes") || v.equals("continue")) {
            return true;
        }
        return v.contains("refine in intake")
                || v.contains("baseline to be confirmed")
                || v.contains("to be confirmed in intake")
                || v.contains("draft — refine")
                || v.contains("draft—refine")
                || v.contains("to be confirmed with coordinator")
                || v.contains("see design notes; confirm modules")
                || v.contains("confirm modules after quick code search")
                || v.contains("key modules to confirm during implementation")
                || v.startsWith("(not drafted yet)")
                || v.contains("starter open questions")
                || (v.contains("reply ok") && v.length() < 80);
    }

    /** Thin auto exploration stubs (not the LLM expansion header). */
    public static boolean looksLikeHollowExploration(String exploration) {
        if (exploration == null || exploration.isBlank()) {
            return true;
        }
        String v = exploration.trim().toLowerCase();
        return v.contains("(auto-generated exploration")
                || (v.contains("reply ok") && exploration.length() < 120);
    }
}
