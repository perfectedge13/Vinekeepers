package com.vinekeepers.workflow.planning;

import java.util.List;
import java.util.Objects;

/**
 * Policy output for at most one coordinator ask: gap id, merge slice, composed text, and presentation mode.
 * {@link ClarificationProjection} is built only from this record (adapter).
 */
public record CanonicalClarificationSelection(
        boolean askUser,
        String gapId,
        /** Profile-relative path {@code artifactId/sectionId/fieldKey} for merge; required when {@link #askUser}. */
        String mergeTargetPath,
        String questionText,
        QuestionMode questionMode,
        boolean blockingGap,
        boolean useStructuredChoices,
        String orchestratorPrompt,
        String choicesJson,
        String metaJson,
        List<String> assumptionsToRecord) {

    public CanonicalClarificationSelection {
        gapId = gapId != null ? gapId.trim() : "";
        mergeTargetPath = mergeTargetPath != null ? mergeTargetPath.trim() : "";
        questionText = questionText != null ? questionText : "";
        orchestratorPrompt = orchestratorPrompt != null ? orchestratorPrompt : "";
        choicesJson = choicesJson != null ? choicesJson : "[]";
        metaJson = metaJson != null ? metaJson : "{}";
        assumptionsToRecord = assumptionsToRecord != null ? List.copyOf(assumptionsToRecord) : List.of();
        Objects.requireNonNull(questionMode, "questionMode");
    }

    public static CanonicalClarificationSelection none() {
        return new CanonicalClarificationSelection(
                false, "", "", "", QuestionMode.OPEN, false, false, "", "[]", "{}", List.of());
    }
}
