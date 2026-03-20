package com.vinekeepers.workflow.actions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.PlanningProposal;
import com.vinekeepers.workflow.planning.PlanningProposalJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies {@link PlanningProposal} entries marked {@code AUTO_APPLY} to {@link com.vinekeepers.state.planning.FeaturePlanState}
 * and rewrites {@code planningProposalsJson} to retain only non-auto proposals (typically {@code CONFIRM}).
 */
public final class ApplyAutoPlanningProposalsAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStore;
    private final WorkProfileRegistry profileRegistry;

    public ApplyAutoPlanningProposalsAction(FeaturePlanStateStore planStore, WorkProfileRegistry profileRegistry) {
        this.planStore = planStore;
        this.profileRegistry = profileRegistry;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("planningAutoApplyNote", "");
        spread.put("planningAutoApplyCount", "0");
        if (planStore == null || profileRegistry == null) {
            spread.put("planningAutoApplyNote", "Plan store or profile registry not available.");
            return spread;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            spread.put("planningAutoApplyNote", "Missing contextId.");
            return spread;
        }
        String json = firstNonBlank(getString(bind, "planningProposalsJson"), getString(state, "planningProposalsJson"));
        List<PlanningProposal> proposals;
        try {
            proposals = PlanningProposalJson.parseList(json);
        } catch (JsonProcessingException e) {
            spread.put("planningAutoApplyNote", "Invalid planningProposalsJson.");
            return spread;
        }
        UpsertArtifactSectionDataAction upsert = new UpsertArtifactSectionDataAction(planStore, profileRegistry);
        Map<String, Object> baseState = new LinkedHashMap<>();
        if (state != null) {
            baseState.putAll(state);
        }
        baseState.put("contextId", contextId);

        int applied = 0;
        List<PlanningProposal> remaining = new ArrayList<>();
        for (PlanningProposal p : proposals) {
            if ("AUTO_APPLY".equalsIgnoreCase(p.getInteraction())) {
                upsert.run(
                        event,
                        baseState,
                        Map.of(
                                "artifactId", p.getArtifactId(),
                                "sectionId", p.getSectionId(),
                                "mode", "replace",
                                "data", Map.of(p.getFieldId(), p.getProposedValue())));
                applied++;
            } else {
                remaining.add(p);
            }
        }
        try {
            spread.put("planningProposalsJson", PlanningProposalJson.toJson(remaining));
        } catch (JsonProcessingException e) {
            spread.put("planningAutoApplyNote", "Failed to serialize remaining proposals.");
            return spread;
        }
        spread.put("planningAutoApplyCount", String.valueOf(applied));
        spread.put(
                "planningAutoApplyNote",
                applied > 0 ? "Auto-applied " + applied + " planning proposal(s)." : "No auto-apply proposals.");
        return spread;
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
