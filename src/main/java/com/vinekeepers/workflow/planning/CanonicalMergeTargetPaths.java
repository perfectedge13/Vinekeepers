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

    /** True when {@code mergeTargetPath} matches the canonical slice for this gap (no fuzzy recovery). */
    public static boolean declaredMergeTargetMatchesGap(String mergeTargetPath, String gapId) {
        if (mergeTargetPath == null || mergeTargetPath.isBlank() || gapId == null || gapId.isBlank()) {
            return false;
        }
        return defaultMergeTargetPath(gapId.trim()).equals(mergeTargetPath.trim());
    }
}
