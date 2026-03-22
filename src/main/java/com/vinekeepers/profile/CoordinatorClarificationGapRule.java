package com.vinekeepers.profile;

import java.util.List;
import java.util.Objects;

/**
 * Declarative rule for coordinator clarification gaps (profile YAML {@code coordinatorClarification.gaps}).
 */
public final class CoordinatorClarificationGapRule {

    private final String id;
    private final boolean blocking;
    private final String questionTemplate;
    /** When non-empty, at least one LLM follow-up line must contain every substring (case-insensitive). */
    private final List<String> hintDetectAllOf;
    /**
     * When non-empty, the gap may also open when every substring appears in canonical text (e.g. user said "both" in a
     * prior answer) even if no hint line matched this cycle.
     */
    private final List<String> canonicalOpenAllOf;
    /**
     * When non-empty, the gap is considered resolved if canonical text (assumptions, exploration, decisions) contains
     * any of these substrings (case-insensitive).
     */
    private final List<String> resolveAnySubstring;
    /** When true (and profile deliberation enables bounded choices), coordinator may use structured choice UI for this gap. */
    private final boolean useBoundedChoiceUi;
    /**
     * When true (and {@link #useBoundedChoiceUi}), allow OR-in-text heuristic for bounded choices. Default false for
     * production coordinator gaps.
     */
    private final boolean inferOrChoices;
    /**
     * When the same gap would repeat the same template as the last merged question, show this text instead (plain English).
     */
    private final String narrowEscalationTemplate;

    public CoordinatorClarificationGapRule(
            String id,
            boolean blocking,
            String questionTemplate,
            List<String> hintDetectAllOf,
            List<String> canonicalOpenAllOf,
            List<String> resolveAnySubstring) {
        this(id, blocking, questionTemplate, hintDetectAllOf, canonicalOpenAllOf, resolveAnySubstring, false, false, "");
    }

    public CoordinatorClarificationGapRule(
            String id,
            boolean blocking,
            String questionTemplate,
            List<String> hintDetectAllOf,
            List<String> canonicalOpenAllOf,
            List<String> resolveAnySubstring,
            boolean useBoundedChoiceUi,
            boolean inferOrChoices,
            String narrowEscalationTemplate) {
        this.id = Objects.requireNonNull(id, "id").trim();
        this.blocking = blocking;
        this.questionTemplate = questionTemplate != null ? questionTemplate.trim() : "";
        this.hintDetectAllOf = hintDetectAllOf != null ? List.copyOf(hintDetectAllOf) : List.of();
        this.canonicalOpenAllOf = canonicalOpenAllOf != null ? List.copyOf(canonicalOpenAllOf) : List.of();
        this.resolveAnySubstring = resolveAnySubstring != null ? List.copyOf(resolveAnySubstring) : List.of();
        this.useBoundedChoiceUi = useBoundedChoiceUi;
        this.inferOrChoices = inferOrChoices;
        this.narrowEscalationTemplate = narrowEscalationTemplate != null ? narrowEscalationTemplate.trim() : "";
    }

    public String getId() {
        return id;
    }

    public boolean isBlocking() {
        return blocking;
    }

    public String getQuestionTemplate() {
        return questionTemplate;
    }

    public List<String> getHintDetectAllOf() {
        return hintDetectAllOf;
    }

    public List<String> getCanonicalOpenAllOf() {
        return canonicalOpenAllOf;
    }

    public List<String> getResolveAnySubstring() {
        return resolveAnySubstring;
    }

    public boolean isUseBoundedChoiceUi() {
        return useBoundedChoiceUi;
    }

    public boolean isInferOrChoices() {
        return inferOrChoices;
    }

    public String getNarrowEscalationTemplate() {
        return narrowEscalationTemplate;
    }
}
