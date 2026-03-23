package com.vinekeepers.workflow.planning.rag;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Resolves the checked-out git branch name for a local repo (best-effort).
 */
public final class GitRepoBranchResolver {

    public String resolve(Path repoRoot, Map<String, Object> state, Map<String, Object> bind) {
        String fromBind = firstNonBlank(get(bind, "branch"), get(bind, "gitBranch"));
        String fromState = firstNonBlank(get(state, "branch"), get(state, "gitBranch"));
        String direct = firstNonBlank(fromBind, fromState);
        if (direct != null && !direct.isBlank()) {
            return direct.trim();
        }
        if (repoRoot == null || !java.nio.file.Files.isDirectory(repoRoot)) {
            return "unknown";
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "-C", repoRoot.toString(), "rev-parse", "--abbrev-ref", "HEAD");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(8, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return "unknown";
            }
            if (p.exitValue() != 0) {
                return "unknown";
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (out.isBlank() || "HEAD".equals(out)) {
                return "unknown";
            }
            return out;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String get(Map<String, Object> m, String k) {
        if (m == null) {
            return null;
        }
        Object v = m.get(k);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
