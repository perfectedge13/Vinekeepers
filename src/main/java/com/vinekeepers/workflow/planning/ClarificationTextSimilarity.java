package com.vinekeepers.workflow.planning;

import java.util.Locale;

/**
 * Duplicate / paraphrase detection for coordinator clarification text (ledger continuity). Lives outside any ranker.
 */
public final class ClarificationTextSimilarity {

    private ClarificationTextSimilarity() {}

    /** Similarity in [0,1] for paraphrase / duplicate detection (e.g. ledger gap evaluation). */
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
}
