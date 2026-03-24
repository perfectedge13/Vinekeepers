package com.vinekeepers.workflow.planning;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Deterministic gap identity: same coordinator rule + merge slice yields the same {@code gapId} across cycles. Primary
 * coordinator gaps use the profile rule id (stable with existing {@code planningGapAskCountsJson} keys); siblings use a
 * deterministic suffix.
 */
public final class CanonicalPlanningGapIds {

    private CanonicalPlanningGapIds() {}

    public static String stableCoordinatorGapId(
            CanonicalGapKind kind, String mergeTargetPath, String ruleId, int siblingIndex) {
        if (ruleId == null || ruleId.isBlank()) {
            String payload = kind.name() + "|" + norm(mergeTargetPath) + "|__noid__|" + Math.max(0, siblingIndex);
            return "cg_" + sha256Short(payload);
        }
        String rid = ruleId.trim();
        if (siblingIndex <= 0) {
            return rid;
        }
        String payload = kind.name() + "|" + norm(mergeTargetPath) + "|" + norm(rid) + "|" + siblingIndex;
        return rid + "~" + sha256Short(payload);
    }

    private static String norm(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static String sha256Short(String payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
