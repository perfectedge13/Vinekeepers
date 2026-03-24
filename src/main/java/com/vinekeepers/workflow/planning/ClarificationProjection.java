package com.vinekeepers.workflow.planning;

import java.util.List;

/**
 * Transport shape for coordinator clarification spread keys and ledger upsert (question text, structured choices, assumptions).
 * Adapter-only: production builds only via {@link #fromSelection(CanonicalClarificationSelection)}.
 */
public record ClarificationProjection(
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
        String questionText,
        /** Stable coordinator gap id when asking; empty when not asking. */
        String canonicalGapId) {

    public static ClarificationProjection fromSelection(CanonicalClarificationSelection s) {
        if (s == null || !s.askUser()) {
            return new ClarificationProjection(
                    false, "", "[]", "{}", 0, s != null ? s.assumptionsToRecord() : List.of(), false, "", "");
        }
        int blocking = s.blockingGap() ? 1 : 0;
        return new ClarificationProjection(
                true,
                s.orchestratorPrompt(),
                s.choicesJson(),
                s.metaJson(),
                blocking,
                s.assumptionsToRecord(),
                s.useStructuredChoices(),
                s.questionText(),
                s.gapId());
    }
}
