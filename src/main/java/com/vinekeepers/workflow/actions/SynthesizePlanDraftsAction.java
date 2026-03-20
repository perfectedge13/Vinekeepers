package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.profile.ArtifactState;
import com.vinekeepers.profile.SectionState;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.repo.RepoWorkspaceStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Deterministic draft fill for common planning artifacts when a local workspace path is available.
 * Does not call external LLMs; reduces discovery prompts by pre-populating empty required fields.
 */
public final class SynthesizePlanDraftsAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final int MAX_LIST_FILES = 24;
    private static final int README_MAX_CHARS = 1200;

    private final FeaturePlanStateStore planStore;
    private final WorkProfileRegistry profileRegistry;

    public SynthesizePlanDraftsAction(FeaturePlanStateStore planStore, WorkProfileRegistry profileRegistry) {
        this.planStore = planStore;
        this.profileRegistry = profileRegistry;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("synthesizePlanDraftsStatus", "SKIPPED");
        spread.put("synthesizePlanDraftsNote", "");
        if (planStore == null || profileRegistry == null) {
            spread.put("synthesizePlanDraftsNote", "Plan store or profile registry not available.");
            return spread;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            spread.put("synthesizePlanDraftsNote", "Missing contextId.");
            return spread;
        }
        FeaturePlanState plan = planStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            spread.put("synthesizePlanDraftsNote", "No FeaturePlanState for context.");
            return spread;
        }
        String localPath = plan.getRepoLocalPath();
        if (localPath == null || localPath.isBlank()) {
            spread.put("synthesizePlanDraftsNote", "No local workspace path on plan.");
            return spread;
        }
        Path root = Path.of(localPath);
        if (!Files.isDirectory(root)) {
            spread.put("synthesizePlanDraftsNote", "Workspace path is not a directory.");
            return spread;
        }
        if (!workspaceLikelyReady(plan.getRepoWorkspaceStatus())) {
            spread.put("synthesizePlanDraftsNote", "Workspace not ready (status: " + plan.getRepoWorkspaceStatus() + ").");
            return spread;
        }
        String profileId = plan.getProfileId();
        if (profileId == null || profileId.isBlank()) {
            spread.put("synthesizePlanDraftsNote", "Plan has no profileId.");
            return spread;
        }
        WorkProfileDefinition profile = profileRegistry.get(profileId).orElse(null);
        if (profile == null) {
            spread.put("synthesizePlanDraftsNote", "Unknown profile: " + profileId);
            return spread;
        }

        UpsertArtifactSectionDataAction upsert = new UpsertArtifactSectionDataAction(planStore, profileRegistry);
        Map<String, Object> baseState = new LinkedHashMap<>();
        if (state != null) {
            baseState.putAll(state);
        }
        baseState.put("contextId", contextId);

        List<String> notes = new ArrayList<>();
        String request = plan.getInitialRequest() != null ? plan.getInitialRequest() : "";
        String readmeSnippet = readReadmeSnippet(root);
        List<String> sampleFiles = listSampleSourceFiles(root);

        if (isFieldEmpty(plan, "overall_plan", "outline", "plan_body")) {
            String body = buildPlanBodyDraft(request, readmeSnippet, sampleFiles);
            upsert.run(event, baseState, Map.of(
                    "artifactId", "overall_plan",
                    "sectionId", "outline",
                    "mode", "replace",
                    "data", Map.of("plan_body", body)));
            notes.add("Drafted overall plan outline.");
        }
        if (isFieldEmpty(plan, "validation_plan", "checks", "validation_notes")) {
            String v = buildValidationDraft(sampleFiles);
            upsert.run(event, baseState, Map.of(
                    "artifactId", "validation_plan",
                    "sectionId", "checks",
                    "mode", "replace",
                    "data", Map.of("validation_notes", v)));
            notes.add("Drafted validation approach.");
        }
        if (isFieldEmpty(plan, "project_context", "context", "context_summary")) {
            String ctx = buildContextSummary(plan, readmeSnippet);
            upsert.run(event, baseState, Map.of(
                    "artifactId", "project_context",
                    "sectionId", "context",
                    "mode", "replace",
                    "data", Map.of("context_summary", ctx)));
            notes.add("Drafted project context summary.");
        }

        if (notes.isEmpty()) {
            spread.put("synthesizePlanDraftsNote", "No empty draft targets; nothing written.");
            return spread;
        }
        spread.put("synthesizePlanDraftsStatus", "OK");
        spread.put("synthesizePlanDraftsNote", String.join(" ", notes));
        return spread;
    }

    private static boolean workspaceLikelyReady(String statusName) {
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

    private static boolean isFieldEmpty(FeaturePlanState plan, String artifactId, String sectionId, String fieldId) {
        ArtifactState art = plan.getArtifacts().get(artifactId);
        if (art == null) {
            return true;
        }
        SectionState sec = art.getSectionsById().get(sectionId);
        if (sec == null) {
            return true;
        }
        Object v = sec.getValues().get(fieldId);
        if (v == null) {
            return true;
        }
        if (v instanceof String s) {
            return s.isBlank();
        }
        return false;
    }

    private static String buildPlanBodyDraft(String request, String readmeSnippet, List<String> files) {
        StringBuilder sb = new StringBuilder();
        sb.append("(Draft — please edit.)\n\n");
        if (request != null && !request.isBlank()) {
            sb.append("**Goal:** ").append(request.trim()).append("\n\n");
        }
        sb.append("**Suggested approach:**\n");
        sb.append("1. Review existing code paths and conventions");
        if (!files.isEmpty()) {
            sb.append(" (see sample paths below).\n");
        } else {
            sb.append(".\n");
        }
        sb.append("2. Implement the change with focused edits and matching style.\n");
        sb.append("3. Run `mvn test` and `mvn compile`; if specs or docs change, run repo validation scripts.\n");
        if (!files.isEmpty()) {
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

    private static String buildValidationDraft(List<String> files) {
        StringBuilder sb = new StringBuilder();
        sb.append("(Draft — please edit.)\n");
        sb.append("- Run `mvn test` and `mvn compile`.\n");
        sb.append("- If configuration, specs, or workflows change: `npm run validate-specs`, `npm run validate-drift`, `npm run validate-docs`.\n");
        if (!files.isEmpty()) {
            sb.append("- Spot-check changes in: ").append(String.join(", ", files.subList(0, Math.min(3, files.size())))).append(".\n");
        }
        return sb.toString();
    }

    private static String buildContextSummary(FeaturePlanState plan, String readmeSnippet) {
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

    private static String readReadmeSnippet(Path root) {
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

    private static List<String> listSampleSourceFiles(Path root) {
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

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }
}
