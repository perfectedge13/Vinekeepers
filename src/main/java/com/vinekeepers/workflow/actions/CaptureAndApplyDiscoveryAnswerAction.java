package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.planning.LightweightPlanningReply;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Applies {@code discoveryAnswerRaw} to the current discovery apply target when kind is {@code REQUIRED_FIELD}.
 */
public final class CaptureAndApplyDiscoveryAnswerAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStateStore;
    private final WorkProfileRegistry workProfileRegistry;

    public CaptureAndApplyDiscoveryAnswerAction(
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
        String answer = firstNonBlank(getString(bind, "discoveryAnswerRaw"), getString(state, "discoveryAnswerRaw"));
        String kind = firstNonBlank(getString(bind, "discoveryApplyKind"), getString(state, "discoveryApplyKind"));
        if (contextId == null || contextId.isBlank()) {
            return "Missing contextId for capture_and_apply_discovery_answer.";
        }
        if (answer == null || answer.isBlank()) {
            return "Missing discoveryAnswerRaw.";
        }
        if (LightweightPlanningReply.isConsentToProceed(answer)) {
            new ExpandPlanningDraftsAction(planStateStore, workProfileRegistry).run(event, state, bind);
            return "OK";
        }
        if ("WORKSPACE".equalsIgnoreCase(kind != null ? kind : "")) {
            return applyWorkspaceDiscoveryNote(contextId, answer);
        }
        if (!"REQUIRED_FIELD".equalsIgnoreCase(kind != null ? kind : "")) {
            return "OK_SKIP_APPLY";
        }
        String artifactId = firstNonBlank(getString(bind, "discoveryApplyArtifactId"), getString(state, "discoveryApplyArtifactId"));
        String sectionId = firstNonBlank(getString(bind, "discoveryApplySectionId"), getString(state, "discoveryApplySectionId"));
        String fieldId = firstNonBlank(getString(bind, "discoveryApplyFieldId"), getString(state, "discoveryApplyFieldId"));
        String mode = firstNonBlank(getString(bind, "discoveryApplyMode"), getString(state, "discoveryApplyMode"));
        if (artifactId == null || artifactId.isBlank()
                || sectionId == null || sectionId.isBlank()
                || fieldId == null || fieldId.isBlank()) {
            return "OK_SKIP_APPLY";
        }
        if (mode == null || mode.isBlank()) {
            mode = "replace";
        }
        Map<String, Object> mergedState = new LinkedHashMap<>();
        if (state != null) {
            mergedState.putAll(state);
        }
        mergedState.put("contextId", contextId);
        UpsertArtifactSectionDataAction upsert = new UpsertArtifactSectionDataAction(planStateStore, workProfileRegistry);
        Map<String, Object> upsertBind = new LinkedHashMap<>();
        upsertBind.put("artifactId", artifactId);
        upsertBind.put("sectionId", sectionId);
        upsertBind.put("mode", mode);
        upsertBind.put("data", Map.of(fieldId, answer));
        return upsert.run(event, mergedState, upsertBind);
    }

    private Object applyWorkspaceDiscoveryNote(String contextId, String answer) {
        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            return "No plan for context.";
        }
        String prev = plan.getRepoAccessNotes() != null ? plan.getRepoAccessNotes().trim() : "";
        String line = "Coordinator discovery answer: " + answer.trim();
        String merged = prev.isBlank() ? line : prev + "\n" + line;
        if (merged.length() > 6000) {
            merged = merged.substring(0, 5997) + "…";
        }
        planStateStore.update(
                plan.withWorkspaceLinkage(
                        plan.getRepoWorkspaceId(),
                        plan.getRepoWorkspaceStatus(),
                        plan.getRepoLocalPath(),
                        merged));
        return "OK";
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
