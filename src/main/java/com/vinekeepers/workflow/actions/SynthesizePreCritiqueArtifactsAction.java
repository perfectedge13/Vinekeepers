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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills v2 planning artifacts with deterministic drafts before role thread posts and critique,
 * only when fields are still empty (does not overwrite user or proposal content).
 */
public final class SynthesizePreCritiqueArtifactsAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStateStore;
    private final WorkProfileRegistry workProfileRegistry;

    public SynthesizePreCritiqueArtifactsAction(
            FeaturePlanStateStore planStateStore,
            WorkProfileRegistry workProfileRegistry) {
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
            return "Missing contextId for synthesize_pre_critique_artifacts.";
        }
        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            return "No FeaturePlanState for contextId: " + contextId;
        }
        String profileId = plan.getProfileId();
        if (profileId == null || profileId.isBlank()) {
            return "FeaturePlanState has no profileId.";
        }
        WorkProfileDefinition profile = workProfileRegistry.get(profileId).orElse(null);
        if (profile == null) {
            return "Unknown work profile: " + profileId;
        }
        if (profile.findSection("architecture_notes", "impact").isEmpty()) {
            return "OK_SKIP_PROFILE";
        }

        String request = plan.getInitialRequest() != null ? plan.getInitialRequest().trim() : "";
        String localPath = plan.getRepoLocalPath();
        Path root = localPath != null && !localPath.isBlank() && Files.isDirectory(Path.of(localPath))
                ? Path.of(localPath)
                : null;
        List<String> sampleFiles = root != null ? PlanningDraftSupport.listSampleSourceFiles(root) : List.of();

        UpsertArtifactSectionDataAction upsert = new UpsertArtifactSectionDataAction(planStateStore, workProfileRegistry);
        Map<String, Object> base = new LinkedHashMap<>();
        if (state != null) {
            base.putAll(state);
        }
        base.put("contextId", contextId);

        plan = planStateStore.getByContextId(contextId).orElse(plan);
        if (isEmptyField(plan, "requirements_spec", "narrative", "current_state_summary")) {
            upsert.run(event, base, Map.of(
                    "artifactId", "requirements_spec",
                    "sectionId", "narrative",
                    "mode", "replace",
                    "data", Map.of("current_state_summary", PlanningDraftSupport.buildCurrentStateDraft(request))));
        }

        plan = planStateStore.getByContextId(contextId).orElse(plan);

        if (isEmptyField(plan, "architecture_notes", "impact", "components_impacted")
                && isEmptyField(plan, "architecture_notes", "impact", "architecture_summary")) {
            String draft = PlanningDraftSupport.buildArchitectureDraft(request, sampleFiles);
            upsert.run(event, base, Map.of(
                    "artifactId", "architecture_notes",
                    "sectionId", "impact",
                    "mode", "replace",
                    "data", Map.of(
                            "components_impacted", "See design notes; confirm modules after quick code search.",
                            "architecture_summary", draft)));
        }

        plan = planStateStore.getByContextId(contextId).orElse(plan);
        if (isEmptyField(plan, "risk_register", "main", "risk_summary")) {
            upsert.run(event, base, Map.of(
                    "artifactId", "risk_register",
                    "sectionId", "main",
                    "mode", "replace",
                    "data", Map.of("risk_summary", PlanningDraftSupport.buildRiskDraft(request))));
        }

        plan = planStateStore.getByContextId(contextId).orElse(plan);
        if (isEmptyField(plan, "open_questions_block", "backlog", "open_questions")) {
            upsert.run(event, base, Map.of(
                    "artifactId", "open_questions_block",
                    "sectionId", "backlog",
                    "mode", "replace",
                    "data", Map.of("open_questions", PlanningDraftSupport.buildOpenQuestionsDraft(request))));
        }

        plan = planStateStore.getByContextId(contextId).orElse(plan);
        ArtifactState decisions = plan.getArtifacts().get("decision_log");
        SectionState decSec = decisions != null ? decisions.getSectionsById().get("decisions") : null;
        boolean noDecisions = decSec == null || decSec.getEntries() == null || decSec.getEntries().isEmpty();
        if (noDecisions) {
            upsert.run(event, base, Map.of(
                    "artifactId", "decision_log",
                    "sectionId", "decisions",
                    "mode", "append",
                    "data", Map.of("decision_text", PlanningDraftSupport.buildSeedDecisionText(request))));
        }

        return "OK";
    }

    private static boolean isEmptyField(FeaturePlanState plan, String artId, String secId, String fieldId) {
        ArtifactState art = plan.getArtifacts().get(artId);
        if (art == null) {
            return true;
        }
        SectionState sec = art.getSectionsById().get(secId);
        if (sec == null) {
            return true;
        }
        Object v = sec.getValues().get(fieldId);
        if (v instanceof String s) {
            return s.isBlank();
        }
        return v == null;
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
