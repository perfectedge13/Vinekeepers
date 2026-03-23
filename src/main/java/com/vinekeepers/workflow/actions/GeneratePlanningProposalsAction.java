package com.vinekeepers.workflow.actions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.ArtifactState;
import com.vinekeepers.profile.SectionState;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.PlanningProposal;
import com.vinekeepers.workflow.planning.PlanningDraftSupport;
import com.vinekeepers.workflow.planning.PlanningProposalJson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds {@link PlanningProposal} list from plan + workspace signals; does not write canonical artifacts.
 */
public final class GeneratePlanningProposalsAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStore;
    private final WorkProfileRegistry profileRegistry;

    public GeneratePlanningProposalsAction(FeaturePlanStateStore planStore, WorkProfileRegistry profileRegistry) {
        this.planStore = planStore;
        this.profileRegistry = profileRegistry;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("planningProposalError", "");
        spread.put("planningProposalsJson", "[]");
        spread.put("planningConfirmQueueJson", "[]");
        spread.put("planningHasConfirmPending", "false");
        spread.put("planningHasAutoApply", "false");
        if (planStore == null || profileRegistry == null) {
            spread.put("planningProposalError", "Plan store or profile registry not available.");
            return spread;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            spread.put("planningProposalError", "Missing contextId.");
            return spread;
        }
        FeaturePlanState plan = planStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            spread.put("planningProposalError", "No FeaturePlanState for context.");
            return spread;
        }
        String profileId = plan.getProfileId();
        if (profileId == null || profileId.isBlank()) {
            spread.put("planningProposalError", "FeaturePlanState has no profileId.");
            return spread;
        }
        if (profileRegistry.get(profileId).isEmpty()) {
            spread.put("planningProposalError", "Unknown profile: " + profileId);
            return spread;
        }

        String request = plan.getInitialRequest() != null ? plan.getInitialRequest().trim() : "";
        String localPath = plan.getRepoLocalPath();
        Path root = localPath != null && !localPath.isBlank() && Files.isDirectory(Path.of(localPath))
                ? Path.of(localPath)
                : null;
        boolean workspaceReady = root != null && PlanningDraftSupport.workspaceLikelyReady(plan.getRepoWorkspaceStatus());
        String readme = workspaceReady ? PlanningDraftSupport.readReadmeSnippet(root) : "";
        List<String> sampleFiles = workspaceReady ? PlanningDraftSupport.listSampleSourceFiles(root) : List.of();

        List<PlanningProposal> proposals = new ArrayList<>();
        List<String> confirmQueue = new ArrayList<>();
        AtomicInteger seq = new AtomicInteger(1);

        maybeAddPlanBody(plan, request, workspaceReady, readme, sampleFiles, proposals, confirmQueue, seq);
        maybeAddValidation(plan, workspaceReady, sampleFiles, proposals, confirmQueue, seq);
        maybeAddContext(plan, workspaceReady, readme, proposals, confirmQueue, seq);
        maybeAddV2ExtendedDrafts(plan, request, workspaceReady, sampleFiles, proposals, confirmQueue, seq);

        try {
            spread.put("planningProposalsJson", PlanningProposalJson.toJson(proposals));
            spread.put("planningConfirmQueueJson", PlanningProposalJson.stringListToJson(confirmQueue));
            spread.put("planningHasConfirmPending", confirmQueue.isEmpty() ? "false" : "true");
            spread.put("planningHasAutoApply", proposals.stream().anyMatch(p -> "AUTO_APPLY".equalsIgnoreCase(p.getInteraction()))
                    ? "true"
                    : "false");
        } catch (JsonProcessingException e) {
            spread.put("planningProposalError", e.getMessage() != null ? e.getMessage() : "proposal json failed");
        }
        return spread;
    }

    private void maybeAddPlanBody(
            FeaturePlanState plan,
            String request,
            boolean workspaceReady,
            String readme,
            List<String> sampleFiles,
            List<PlanningProposal> proposals,
            List<String> confirmQueue,
            AtomicInteger seq) {
        if (!isFieldEmpty(plan, "overall_plan", "outline", "plan_body")) {
            return;
        }
        if (request.isBlank() && !workspaceReady) {
            return;
        }
        String draft = PlanningDraftSupport.buildPlanBodyDraft(request, readme, sampleFiles);
        if (draft.isBlank()) {
            return;
        }
        String id = "prop-" + seq.getAndIncrement();
        if (workspaceReady) {
            proposals.add(new PlanningProposal(
                    id,
                    "overall_plan",
                    "outline",
                    "plan_body",
                    draft,
                    "HIGH",
                    "AUTO_APPLY",
                    buildSources(true, readme, sampleFiles),
                    "Draft from your request and repo context (README / sample paths).",
                    ""));
        } else {
            String leanDraft = PlanningDraftSupport.buildPlanBodyDraft(request, "", List.of());
            proposals.add(new PlanningProposal(
                    id,
                    "overall_plan",
                    "outline",
                    "plan_body",
                    leanDraft,
                    "MEDIUM",
                    "CONFIRM",
                    List.of("INITIAL_REQUEST"),
                    "Workspace is not ready yet; this draft uses your request only. Reply **OK** or **continue** to record it, or paste an edited version.",
                    ""));
            confirmQueue.add(id);
        }
    }

    private void maybeAddValidation(
            FeaturePlanState plan,
            boolean workspaceReady,
            List<String> sampleFiles,
            List<PlanningProposal> proposals,
            List<String> confirmQueue,
            AtomicInteger seq) {
        if (!isFieldEmpty(plan, "validation_plan", "checks", "validation_notes")) {
            return;
        }
        String draft = PlanningDraftSupport.buildValidationDraft(workspaceReady ? sampleFiles : List.of());
        if (draft.isBlank()) {
            return;
        }
        String id = "prop-" + seq.getAndIncrement();
        if (workspaceReady) {
            proposals.add(new PlanningProposal(
                    id,
                    "validation_plan",
                    "checks",
                    "validation_notes",
                    draft,
                    "HIGH",
                    "AUTO_APPLY",
                    buildSources(false, "", sampleFiles),
                    "Draft validation steps aligned with sample repo paths.",
                    ""));
        } else {
            proposals.add(new PlanningProposal(
                    id,
                    "validation_plan",
                    "checks",
                    "validation_notes",
                    draft,
                    "MEDIUM",
                    "CONFIRM",
                    List.of("INITIAL_REQUEST"),
                    "Generic validation draft without a local workspace. Reply OK to record it, or paste an edited version.",
                    ""));
            confirmQueue.add(id);
        }
    }

    private void maybeAddContext(
            FeaturePlanState plan,
            boolean workspaceReady,
            String readme,
            List<PlanningProposal> proposals,
            List<String> confirmQueue,
            AtomicInteger seq) {
        if (!isFieldEmpty(plan, "project_context", "context", "context_summary")) {
            return;
        }
        String draft = PlanningDraftSupport.buildContextSummary(plan, readme);
        if (draft.replace("(Draft — please edit.)", "").trim().isBlank()) {
            return;
        }
        String id = "prop-" + seq.getAndIncrement();
        if (workspaceReady && (readme != null && !readme.isBlank() || plan.getRepoRef() != null)) {
            proposals.add(new PlanningProposal(
                    id,
                    "project_context",
                    "context",
                    "context_summary",
                    draft,
                    "HIGH",
                    "AUTO_APPLY",
                    buildSources(true, readme, List.of()),
                    "Draft context from repository metadata and README.",
                    ""));
        } else {
            String lean = PlanningDraftSupport.buildContextSummary(plan, "");
            proposals.add(new PlanningProposal(
                    id,
                    "project_context",
                    "context",
                    "context_summary",
                    lean,
                    "MEDIUM",
                    "CONFIRM",
                    List.of("INITIAL_REQUEST"),
                    "Lightweight context draft. Reply **OK** or **continue** to record it, or paste an edited version.",
                    ""));
            confirmQueue.add(id);
        }
    }

    private void maybeAddV2ExtendedDrafts(
            FeaturePlanState plan,
            String request,
            boolean workspaceReady,
            List<String> sampleFiles,
            List<PlanningProposal> proposals,
            List<String> confirmQueue,
            AtomicInteger seq) {
        if (profileRegistry == null || plan.getProfileId() == null || plan.getProfileId().isBlank()) {
            return;
        }
        if (profileRegistry.get(plan.getProfileId()).flatMap(p -> p.findSection("architecture_notes", "impact")).isEmpty()) {
            return;
        }
        if (isFieldEmpty(plan, "architecture_notes", "impact", "architecture_summary")) {
            String draft = PlanningDraftSupport.buildArchitectureDraft(request, workspaceReady ? sampleFiles : List.of());
            String id = "prop-" + seq.getAndIncrement();
            if (workspaceReady) {
                proposals.add(new PlanningProposal(
                        id,
                        "architecture_notes",
                        "impact",
                        "architecture_summary",
                        draft,
                        "HIGH",
                        "AUTO_APPLY",
                        buildSources(false, "", sampleFiles),
                        "Draft architecture notes from request and sample paths.",
                        ""));
            } else {
                proposals.add(new PlanningProposal(
                        id,
                        "architecture_notes",
                        "impact",
                        "architecture_summary",
                        draft,
                        "MEDIUM",
                        "CONFIRM",
                        List.of("INITIAL_REQUEST"),
                        "Architecture draft without local workspace. Reply OK or paste edits.",
                        ""));
                confirmQueue.add(id);
            }
        }
        if (isFieldEmpty(plan, "architecture_notes", "impact", "components_impacted")) {
            String id = "prop-" + seq.getAndIncrement();
            String hint = "Infer from request and repo layout; refine after quick code search.";
            proposals.add(new PlanningProposal(
                    id,
                    "architecture_notes",
                    "impact",
                    "components_impacted",
                    hint,
                    "HIGH",
                    workspaceReady ? "AUTO_APPLY" : "CONFIRM",
                    List.of("INITIAL_REQUEST"),
                    "Placeholder components line; edit if you know exact modules.",
                    ""));
            if (!workspaceReady) {
                confirmQueue.add(id);
            }
        }
        if (isFieldEmpty(plan, "risk_register", "main", "risk_summary")) {
            String draft = PlanningDraftSupport.buildRiskDraft(request);
            String id = "prop-" + seq.getAndIncrement();
            proposals.add(new PlanningProposal(
                    id,
                    "risk_register",
                    "main",
                    "risk_summary",
                    draft,
                    "HIGH",
                    workspaceReady ? "AUTO_APPLY" : "CONFIRM",
                    List.of("INITIAL_REQUEST"),
                    "Draft risk / edge-case list.",
                    ""));
            if (!workspaceReady) {
                confirmQueue.add(id);
            }
        }
    }

    private static List<String> buildSources(boolean includeReadme, String readme, List<String> sampleFiles) {
        List<String> s = new ArrayList<>();
        s.add("INITIAL_REQUEST");
        if (includeReadme && readme != null && !readme.isBlank()) {
            s.add("README");
        }
        if (sampleFiles != null && !sampleFiles.isEmpty()) {
            s.add("FILE_TREE");
        }
        return List.copyOf(s);
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
