package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.planning.PlanningDraftSupport;
import com.vinekeepers.workflow.planreview.PlanningArtifactTexts;
import com.vinekeepers.workflow.planning.RequestExplorationSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the {@code request_exploration} artifact (v2+) before synthesis and packet posting.
 */
public final class BuildRequestExplorationAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStateStore;
    private final WorkProfileRegistry workProfileRegistry;

    public BuildRequestExplorationAction(FeaturePlanStateStore planStateStore, WorkProfileRegistry workProfileRegistry) {
        this.planStateStore = planStateStore;
        this.workProfileRegistry = workProfileRegistry;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        if (planStateStore == null || workProfileRegistry == null) {
            return "Plan store or work profile registry not available.";
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            return "Missing contextId for build_request_exploration.";
        }
        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            return "No FeaturePlanState for contextId: " + contextId;
        }
        String profileId = plan.getProfileId();
        if (profileId == null || profileId.isBlank()) {
            return "OK_SKIP";
        }
        WorkProfileDefinition profile = workProfileRegistry.get(profileId).orElse(null);
        if (profile == null || profile.findSection("request_exploration", "analysis").isEmpty()) {
            return "OK_SKIP_PROFILE";
        }

        String existing = PlanningArtifactTexts.artifactField(plan, "request_exploration", "analysis", "exploration_body");
        if (existing != null && !existing.isBlank() && !looksLikeStub(existing)) {
            return "OK_SKIP_EXISTING";
        }

        String request = plan.getInitialRequest() != null ? plan.getInitialRequest().trim() : "";
        String localPath = plan.getRepoLocalPath();
        Path root = localPath != null && !localPath.isBlank() && Files.isDirectory(Path.of(localPath))
                ? Path.of(localPath)
                : null;
        List<String> sampleFiles = root != null ? PlanningDraftSupport.listSampleSourceFiles(root) : List.of();
        String readme = root != null ? RequestExplorationSupport.readSnippet(root, 900) : "";

        String body = RequestExplorationSupport.buildExplorationBody(request, readme, sampleFiles);

        UpsertArtifactSectionDataAction upsert = new UpsertArtifactSectionDataAction(planStateStore, workProfileRegistry);
        Map<String, Object> base = new LinkedHashMap<>();
        if (state != null) {
            base.putAll(state);
        }
        base.put("contextId", contextId);
        upsert.run(
                event,
                base,
                Map.of(
                        "artifactId",
                        "request_exploration",
                        "sectionId",
                        "analysis",
                        "mode",
                        "replace",
                        "data",
                        Map.of("exploration_body", body)));
        return "OK";
    }

    private static boolean looksLikeStub(String existing) {
        return existing.contains("(Auto-generated exploration");
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null ? b : "";
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
