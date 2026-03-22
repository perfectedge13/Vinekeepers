package com.vinekeepers.workflow.convergence;

import com.vinekeepers.state.workflow.ProgressDedupeHelper;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Fingerprints a slice of workflow state to detect user-visible deltas (generic anti-spam helper).
 */
public final class VisibleDeltaTracker {

    private VisibleDeltaTracker() {}

    public static String fingerprintSlice(Map<String, Object> state, Set<String> keys) {
        if (state == null || keys == null || keys.isEmpty()) {
            return ProgressDedupeHelper.sha256Hex("");
        }
        Map<String, String> sorted = new TreeMap<>();
        for (String k : keys) {
            if (k == null || k.isBlank()) {
                continue;
            }
            Object v = state.get(k);
            sorted.put(k, v != null ? v.toString() : "");
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        return ProgressDedupeHelper.sha256Hex(sb.toString());
    }

    public static boolean hasDelta(String previousFingerprint, String currentFingerprint) {
        return !Objects.equals(
                previousFingerprint != null ? previousFingerprint.trim() : "",
                currentFingerprint != null ? currentFingerprint.trim() : "");
    }
}
