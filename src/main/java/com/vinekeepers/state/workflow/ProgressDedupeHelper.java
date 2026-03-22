package com.vinekeepers.state.workflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Generic content-hash dedupe for user-visible progress lines (workflow-agnostic).
 */
public final class ProgressDedupeHelper {

    private ProgressDedupeHelper() {}

    public static String normalizeProgressBody(String full) {
        if (full == null) {
            return "";
        }
        return full.replace("\r\n", "\n").trim();
    }

    public static String sha256Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * @param lastPostedHash previous fingerprint stored in state (e.g. planningLastProgressPostHash)
     * @param newFingerprint fingerprint of candidate body
     * @return true if the post should be sent (first time or content changed)
     */
    public static boolean isPostWorthy(String lastPostedHash, String newFingerprint) {
        if (newFingerprint == null || newFingerprint.isBlank()) {
            return true;
        }
        if (lastPostedHash == null || lastPostedHash.isBlank()) {
            return true;
        }
        return !newFingerprint.trim().equals(lastPostedHash.trim());
    }

    public static String fingerprintForBody(String normalizedBody) {
        return sha256Hex(normalizeProgressBody(normalizedBody));
    }
}
