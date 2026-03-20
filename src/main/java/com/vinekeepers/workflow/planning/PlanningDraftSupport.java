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
            sb.append("Workspace status: ").append(plan.getRepoWorkspaceStatus()).append(". ");
        }
        if (readmeSnippet != null && !readmeSnippet.isBlank()) {
            sb.append("\n").append(readmeSnippet.trim());
        }
        return sb.toString();
    }
}
