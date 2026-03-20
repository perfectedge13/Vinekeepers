package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.profile.ArtifactState;
import com.vinekeepers.profile.SectionState;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.planning.PlanningDraftSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic draft fill for common planning artifacts when a local workspace path is available.
 * Does not call external LLMs; reduces discovery prompts by pre-populating empty required fields.
 * Prefer {@code generate_planning_proposals} + {@code apply_auto_planning_proposals} in new workflows.
 */
public final class SynthesizePlanDraftsAction implements com.vinekeepers.workflow.WorkflowAction {

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
        if (!PlanningDraftSupport.workspaceLikelyReady(plan.getRepoWorkspaceStatus())) {
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
        String readmeSnippet = PlanningDraftSupport.readReadmeSnippet(root);
        List<String> sampleFiles = PlanningDraftSupport.listSampleSourceFiles(root);

        if (isFieldEmpty(plan, "overall_plan", "outline", "plan_body")) {
            String body = PlanningDraftSupport.buildPlanBodyDraft(request, readmeSnippet, sampleFiles);
            upsert.run(event, baseState, Map.of(
                    "artifactId", "overall_plan",
                    "sectionId", "outline",
                    "mode", "replace",
                    "data", Map.of("plan_body", body)));
            notes.add("Drafted overall plan outline.");
        }
        if (isFieldEmpty(plan, "validation_plan", "checks", "validation_notes")) {
            String v = PlanningDraftSupport.buildValidationDraft(sampleFiles);
            upsert.run(event, baseState, Map.of(
                    "artifactId", "validation_plan",
                    "sectionId", "checks",
                    "mode", "replace",
                    "data", Map.of("validation_notes", v)));
            notes.add("Drafted validation approach.");
        }
        if (isFieldEmpty(plan, "project_context", "context", "context_summary")) {
            String ctx = PlanningDraftSupport.buildContextSummary(plan, readmeSnippet);
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
