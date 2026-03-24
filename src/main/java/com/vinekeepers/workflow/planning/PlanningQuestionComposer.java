package com.vinekeepers.workflow.planning;

import com.vinekeepers.workflow.discovery.ClarificationPromptQualityGate;

/**
 * Minimal presentation helper for canonical clarification text.
 * This class must not choose, rank, or synthesize questions from gaps.
 */
public final class PlanningQuestionComposer {

    private PlanningQuestionComposer() {}

    public static String presentCanonicalQuestion(String raw) {
        String normalized = normalizeClarificationQuestion(raw);
        if (normalized.isBlank()) {
            return "";
        }
        return ClarificationPromptQualityGate.acceptableClarificationCandidate(normalized) ? normalized : "";
    }

    /**
     * Normalization for internal dedupe and presentation trimming only.
     */
    public static String normalizeClarificationQuestion(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\\s+", " ").trim();
    }
}
