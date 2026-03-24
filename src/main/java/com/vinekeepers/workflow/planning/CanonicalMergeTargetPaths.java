package com.vinekeepers.workflow.planning;

/**
 * Profile-relative merge slice for coordinator clarification (v1: single decision_log append target unless extended).
 */
public final class CanonicalMergeTargetPaths {

    private CanonicalMergeTargetPaths() {}

    /** Default v1 merge: append structured decision line for coordinator answers. */
    public static String defaultMergeTargetPath(@SuppressWarnings("unused") String gapId) {
        return "decision_log/decisions/decision_text";
    }
}
