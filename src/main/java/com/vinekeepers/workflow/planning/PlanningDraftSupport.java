package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.repo.RepoWorkspaceStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Deterministic planning draft text from repo workspace and/or request (shared by synthesis and proposal actions).
 */
public final class PlanningDraftSupport {

    private static final int MAX_LIST_FILES = 24;
    private static final int README_MAX_CHARS = 1200;

    private PlanningDraftSupport() {
    }

    /** Short plain-language explanation for Discord / drafts (raw enum names are confusing). */
    public static String humanizeRepoWorkspaceStatus(String statusName) {
        if (statusName == null || statusName.isBlank()) {
            return "";
        }
        try {
            return switch (RepoWorkspaceStatus.valueOf(statusName.trim())) {
                case UNRESOLVED -> "Not resolved yet";
                case RESOLVED_LOCAL -> "Local path resolved";
                case MATERIALIZED -> "Materialized (files on disk)";
                case UNAVAILABLE -> "Unavailable";
                case FAILED -> "Failed";
            };
        } catch (IllegalArgumentException e) {
            return statusName.trim();
        }
    }

    public static boolean workspaceLikelyReady(String statusName) {
        if (statusName == null || statusName.isBlank()) {
            return false;
        }
        try {
            RepoWorkspaceStatus s = RepoWorkspaceStatus.valueOf(statusName.trim());
            return s == RepoWorkspaceStatus.MATERIALIZED || s == RepoWorkspaceStatus.RESOLVED_LOCAL;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String readReadmeSnippet(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return "";
        }
        for (String name : List.of("README.md", "README.MD", "readme.md")) {
            Path p = root.resolve(name);
            if (Files.isRegularFile(p)) {
                try {
                    String text = Files.readString(p);
                    return text.length() > README_MAX_CHARS ? text.substring(0, README_MAX_CHARS) + "…" : text;
                } catch (IOException ignored) {
                    return "";
                }
            }
        }
        return "";
    }

    public static List<String> listSampleSourceFiles(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root, 4)) {
            out = walk
                    .filter(Files::isRegularFile)
                    .map(root::relativize)
                    .map(Path::toString)
                    .filter(p -> p.endsWith(".java") || p.endsWith(".yml") || p.endsWith(".yaml") || p.endsWith(".md"))
                    .filter(p -> !p.contains("target") && !p.contains("node_modules") && !p.contains(".git"))
                    .limit(MAX_LIST_FILES)
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
        return out;
    }

    public static String buildPlanBodyDraft(String request, String readmeSnippet, List<String> files) {
        StringBuilder sb = new StringBuilder();
        sb.append("(Draft — please edit.)\n\n");
        if (request != null && !request.isBlank()) {
            sb.append("**Goal:** ").append(request.trim()).append("\n\n");
        }
        sb.append("**Suggested approach:**\n");
        sb.append("1. Review existing code paths and conventions");
        if (files != null && !files.isEmpty()) {
            sb.append(" (see sample paths below).\n");
        } else {
            sb.append(".\n");
        }
        sb.append("2. Implement the change with focused edits and matching style.\n");
        sb.append("3. Run `mvn test` and `mvn compile`; if specs or docs change, run repo validation scripts.\n");
        if (files != null && !files.isEmpty()) {
            sb.append("\n**Sample paths in repo:**\n");
            for (String f : files) {
                sb.append("- ").append(f).append("\n");
            }
        }
        if (readmeSnippet != null && !readmeSnippet.isBlank()) {
            sb.append("\n**README excerpt:**\n").append(readmeSnippet.trim()).append("\n");
        }
        return sb.toString();
    }

    public static String buildValidationDraft(List<String> files) {
        StringBuilder sb = new StringBuilder();
        sb.append("(Draft — please edit.)\n");
        sb.append("- Run `mvn test` and `mvn compile`.\n");
        sb.append("- If configuration, specs, or workflows change: `npm run validate-specs`, `npm run validate-drift`, `npm run validate-docs`.\n");
        if (files != null && !files.isEmpty()) {
            sb.append("- Spot-check changes in: ")
                    .append(String.join(", ", files.subList(0, Math.min(3, files.size()))))
                    .append(".\n");
        }
        return sb.toString();
    }

    public static String buildContextSummary(FeaturePlanState plan, String readmeSnippet) {
        StringBuilder sb = new StringBuilder();
        sb.append("(Draft — please edit.)\n");
        if (plan.getRepoRef() != null && !plan.getRepoRef().isBlank()) {
            sb.append("Repository: ").append(plan.getRepoRef()).append(". ");
        }
        if (plan.getRepoWorkspaceStatus() != null && !plan.getRepoWorkspaceStatus().isBlank()) {
            sb.append("Workspace status: ")
                    .append(humanizeRepoWorkspaceStatus(plan.getRepoWorkspaceStatus()))
                    .append(". ");
        }
        if (readmeSnippet != null && !readmeSnippet.isBlank()) {
            sb.append("\n").append(readmeSnippet.trim());
        }
        return sb.toString();
    }

    /** Draft architecture impact from request and sample paths (v2 profile). */
    public static String buildArchitectureDraft(String request, List<String> sampleFiles) {
        StringBuilder sb = new StringBuilder();
        sb.append("(Draft — refine before approval.)\n\n");
        if (request != null && !request.isBlank()) {
            sb.append("**Goal (from request):** ").append(request.trim()).append("\n\n");
        }
        sb.append("**Likely touchpoints:** infer from request; confirm packages/modules.\n");
        if (sampleFiles != null && !sampleFiles.isEmpty()) {
            sb.append("\n**Candidate paths:**\n");
            int n = Math.min(8, sampleFiles.size());
            for (int i = 0; i < n; i++) {
                sb.append("- ").append(sampleFiles.get(i)).append("\n");
            }
        }
        sb.append("\n**Design:** Prefer smallest change, match repo conventions, keep spec/docs in sync if behavior changes.\n");
        return sb.toString();
    }

    /** Draft risk / edge-case block from request. */
    public static String buildRiskDraft(String request) {
        StringBuilder sb = new StringBuilder();
        sb.append("(Draft — refine before approval.)\n");
        sb.append("- Regression in existing workflows or connectors. **Mitigation:** targeted tests and staged rollout.\n");
        sb.append("- Config/spec drift if YAML, specs, or mkdoc change. **Mitigation:** run validate-specs / validate-drift.\n");
        sb.append("- Discord or Cursor API failures; retry and user-visible errors. **Mitigation:** clear error copy and retry path.\n");
        if (request != null && !request.isBlank()) {
            String t = request.trim();
            sb.append("- Request-specific: ").append(t.length() > 200 ? t.substring(0, 200) + "…" : t).append("\n");
        }
        return sb.toString();
    }

    /** Draft current-state / baseline before the change (v2 narrative). */
    public static String buildCurrentStateDraft(String request) {
        StringBuilder sb = new StringBuilder();
        sb.append("(Draft — please edit.)\n");
        sb.append("Summarize how the system behaves today for the area touched by this request.\n");
        sb.append("Call out gaps, bugs, or missing capability that motivate the change.\n");
        if (request != null && !request.isBlank()) {
            String r = request.trim();
            sb.append("\n**Request (for context):** ")
                    .append(r.length() > 400 ? r.substring(0, 400) + "…" : r)
                    .append("\n");
        }
        return sb.toString();
    }

    /** Replaces thin intake placeholders with request-grounded scope text. */
    public static String buildScopeDraftFromRequest(String request) {
        String r = request != null ? request.trim() : "";
        StringBuilder sb = new StringBuilder();
        sb.append("**In scope:** Implement the change described in the request (below).\n");
        sb.append("**Out of scope:** Unrelated refactors, style-only edits, and features not implied by the request.\n");
        sb.append("**Compatibility:** Preserve existing public behavior unless the request explicitly changes it.\n");
        if (!r.isBlank()) {
            sb.append("\n**Request:** ").append(r.length() > 500 ? r.substring(0, 500) + "…" : r);
        }
        return sb.toString();
    }

    /** Replaces thin intake placeholders with testable acceptance bullets. */
    public static String buildAcceptanceDraftFromRequest(String request) {
        String r = request != null ? request.trim() : "";
        StringBuilder sb = new StringBuilder();
        sb.append("- Primary user flow for this feature works end-to-end.\n");
        sb.append("- `mvn test` and `mvn compile` pass.\n");
        sb.append("- Config/spec/docs updated when behavior or contracts change.\n");
        if (!r.isBlank()) {
            sb.append("- Delivers: ")
                    .append(r.length() > 280 ? r.substring(0, 280) + "…" : r)
                    .append("\n");
        }
        return sb.toString();
    }

    /** Seed decision row text for empty decision log (v2). */
    public static String buildSeedDecisionText(String request) {
        String r = request != null ? request.trim() : "";
        if (r.length() > 160) {
            r = r.substring(0, 160) + "…";
        }
        return "Proceed with implementation aligned to the approved planning packet"
                + (r.isBlank() ? "." : (" for: " + r));
    }
}
