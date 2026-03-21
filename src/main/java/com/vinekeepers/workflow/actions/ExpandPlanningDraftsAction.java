package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.profile.ArtifactState;
import com.vinekeepers.profile.SectionState;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.planning.PlanningDraftSupport;
import com.vinekeepers.workflow.planning.PlanningPlaceholderDetection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Overwrites placeholder-heavy artifact fields with stronger drafts from the request and repo context.
 */
public final class ExpandPlanningDraftsAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStateStore;
    private final WorkProfileRegistry workProfileRegistry;

    public ExpandPlanningDraftsAction(FeaturePlanStateStore planStateStore, WorkProfileRegistry workProfileRegistry) {
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
            return "Missing contextId for expand_planning_drafts.";
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
        if (profile == null) {
            return "OK_SKIP";
        }

        String request = plan.getInitialRequest() != null ? plan.getInitialRequest().trim() : "";
        String localPath = plan.getRepoLocalPath();
        Path root = localPath != null && !localPath.isBlank() && Files.isDirectory(Path.of(localPath))
                ? Path.of(localPath)
                : null;
        List<String> sampleFiles = root != null ? PlanningDraftSupport.listSampleSourceFiles(root) : List.of();
        String readme = root != null ? PlanningDraftSupport.readReadmeSnippet(root) : "";

        UpsertArtifactSectionDataAction upsert = new UpsertArtifactSectionDataAction(planStateStore, workProfileRegistry);
        Map<String, Object> base = new LinkedHashMap<>();
        if (state != null) {
            base.putAll(state);
        }
        base.put("contextId", contextId);

        maybeReplace(
                upsert,
                event,
                base,
                plan,
                "requirements_spec",
                "narrative",
                "scope_summary",
                PlanningDraftSupport.buildScopeDraftFromRequest(request));
        plan = planStateStore.getByContextId(contextId).orElse(plan);
        maybeReplace(
                upsert,
                event,
                base,
                plan,
                "requirements_spec",
                "narrative",
                "acceptance_criteria",
                PlanningDraftSupport.buildAcceptanceDraftFromRequest(request));

        plan = planStateStore.getByContextId(contextId).orElse(plan);
        if (profile.findSection("requirements_spec", "narrative").isPresent()) {
            maybeReplace(
                    upsert,
                    event,
                    base,
                    plan,
                    "requirements_spec",
                    "narrative",
                    "current_state_summary",
                    PlanningDraftSupport.buildCurrentStateDraft(request));
        }

        plan = planStateStore.getByContextId(contextId).orElse(plan);
        if (profile.findSection("architecture_notes", "impact").isPresent()) {
            String comps = fieldString(plan, "architecture_notes", "impact", "components_impacted");
            String arch = fieldString(plan, "architecture_notes", "impact", "architecture_summary");
            boolean badComps = PlanningPlaceholderDetection.looksLikePlaceholder(comps)
                    || "See design notes; confirm modules after quick code search.".equalsIgnoreCase(comps.trim());
            boolean badArch = PlanningPlaceholderDetection.looksLikePlaceholder(arch) || arch.length() < 40;
            if (badComps || badArch) {
                String list = sampleFiles.isEmpty()
                        ? "Confirm packages/modules after a quick repo search."
                        : String.join(", ", sampleFiles.subList(0, Math.min(6, sampleFiles.size())));
                String archDraft = PlanningDraftSupport.buildArchitectureDraft(request, sampleFiles);
                Map<String, Object> data = new LinkedHashMap<>();
                if (badComps) {
                    data.put("components_impacted", list);
                }
                if (badArch) {
                    data.put("architecture_summary", archDraft);
                }
                upsert.run(
                        event,
                        base,
                        Map.of(
                                "artifactId",
                                "architecture_notes",
                                "sectionId",
                                "impact",
                                "mode",
                                "replace",
                                "data",
                                data));
            }
        }

        plan = planStateStore.getByContextId(contextId).orElse(plan);
        maybeReplace(
                upsert,
                event,
                base,
                plan,
                "overall_plan",
                "outline",
                "plan_body",
                PlanningDraftSupport.buildPlanBodyDraft(request, readme, sampleFiles));

        plan = planStateStore.getByContextId(contextId).orElse(plan);
        maybeReplace(
                upsert,
                event,
                base,
                plan,
                "validation_plan",
                "checks",
                "validation_notes",
                PlanningDraftSupport.buildValidationDraft(sampleFiles));

        plan = planStateStore.getByContextId(contextId).orElse(plan);
        maybeReplace(
                upsert,
                event,
                base,
                plan,
                "project_context",
                "context",
                "context_summary",
                PlanningDraftSupport.buildContextSummary(plan, readme));

        if (profile.findSection("risk_register", "main").isPresent()) {
            plan = planStateStore.getByContextId(contextId).orElse(plan);
            maybeReplace(
                    upsert,
                    event,
                    base,
                    plan,
                    "risk_register",
                    "main",
                    "risk_summary",
                    PlanningDraftSupport.buildRiskDraft(request));
        }
        if (profile.findSection("open_questions_block", "backlog").isPresent()) {
            plan = planStateStore.getByContextId(contextId).orElse(plan);
            maybeReplace(
                    upsert,
                    event,
                    base,
                    plan,
                    "open_questions_block",
                    "backlog",
                    "open_questions",
                    PlanningDraftSupport.buildOpenQuestionsDraft(request));
        }

        return "OK";
    }

    private static void maybeReplace(
            UpsertArtifactSectionDataAction upsert,
            Event event,
            Map<String, Object> base,
            FeaturePlanState plan,
            String artifactId,
            String sectionId,
            String fieldId,
            String draft) {
        String cur = fieldString(plan, artifactId, sectionId, fieldId);
        if (!PlanningPlaceholderDetection.looksLikePlaceholder(cur) && cur.length() >= 40) {
            return;
        }
        upsert.run(
                event,
                base,
                Map.of(
                        "artifactId",
                        artifactId,
                        "sectionId",
                        sectionId,
                        "mode",
                        "replace",
                        "data",
                        Map.of(fieldId, draft)));
    }

    private static String fieldString(FeaturePlanState plan, String artId, String secId, String fieldId) {
        ArtifactState art = plan.getArtifacts().get(artId);
        if (art == null) {
            return "";
        }
        SectionState sec = art.getSectionsById().get(secId);
        if (sec == null) {
            return "";
        }
        Object v = sec.getValues().get(fieldId);
        return v != null ? v.toString() : "";
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
