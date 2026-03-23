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
 * Auto-applies all pending {@code CONFIRM} planning proposals using each proposal's {@code proposedValue} (draft-first
 * intake: no user round-trip before the first autonomous draft).
 */
public final class AcceptPendingPlanningConfirmationsAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStore;
    private final WorkProfileRegistry profileRegistry;

    public AcceptPendingPlanningConfirmationsAction(FeaturePlanStateStore planStore, WorkProfileRegistry profileRegistry) {
        this.planStore = planStore;
        this.profileRegistry = profileRegistry;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("planningProposalAutoConfirmError", "");
        spread.put("planningProposalAutoConfirmCount", "0");
        spread.put("planningHasConfirmPending", "false");
        spread.put("planningConfirmQueueJson", "[]");
        if (planStore == null || profileRegistry == null) {
            spread.put("planningProposalAutoConfirmError", "Plan store or profile registry not available.");
            return spread;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            spread.put("planningProposalAutoConfirmError", "Missing contextId.");
            return spread;
        }
        List<String> queue;
        try {
            queue = PlanningProposalJson.parseStringList(
                    firstNonBlank(getString(bind, "planningConfirmQueueJson"), getString(state, "planningConfirmQueueJson")));
        } catch (JsonProcessingException e) {
            spread.put("planningProposalAutoConfirmError", "Invalid planningConfirmQueueJson.");
            return spread;
        }
        if (queue.isEmpty()) {
            return spread;
        }
        List<PlanningProposal> proposals;
        try {
            proposals = PlanningProposalJson.parseList(
                    firstNonBlank(getString(bind, "planningProposalsJson"), getString(state, "planningProposalsJson")));
        } catch (JsonProcessingException e) {
            spread.put("planningProposalAutoConfirmError", "Invalid planningProposalsJson.");
            return spread;
        }
        Map<String, Object> baseState = new LinkedHashMap<>();
        if (state != null) {
            baseState.putAll(state);
        }
        baseState.put("contextId", contextId);
        UpsertArtifactSectionDataAction upsert = new UpsertArtifactSectionDataAction(planStore, profileRegistry);
        ExpandPlanningDraftsAction expand = new ExpandPlanningDraftsAction(planStore, profileRegistry);
        int applied = 0;
        List<PlanningProposal> remainingProposals = new ArrayList<>(proposals);
        for (String proposalId : queue) {
            PlanningProposal match = null;
            for (PlanningProposal p : remainingProposals) {
                if (proposalId.equals(p.getProposalId()) && "CONFIRM".equalsIgnoreCase(p.getInteraction())) {
                    match = p;
                    break;
                }
            }
            if (match == null) {
                continue;
            }
            String valueToStore = firstNonBlank(match.getProposedValue(), "");
            if (valueToStore.isBlank()) {
                continue;
            }
            upsert.run(
                    event,
                    baseState,
                    Map.of(
                            "artifactId", match.getArtifactId(),
                            "sectionId", match.getSectionId(),
                            "mode", "replace",
                            "data", Map.of(match.getFieldId(), valueToStore)));
            expand.run(event, baseState, bind);
            final PlanningProposal appliedProposal = match;
            remainingProposals.removeIf(p -> p.getProposalId().equals(appliedProposal.getProposalId()));
            applied++;
        }
        try {
            spread.put("planningProposalsJson", PlanningProposalJson.toJson(remainingProposals));
            spread.put("planningConfirmQueueJson", "[]");
            spread.put("planningHasConfirmPending", "false");
            spread.put("planningProposalAutoConfirmCount", String.valueOf(applied));
        } catch (JsonProcessingException e) {
            spread.put("planningProposalAutoConfirmError", "Failed to serialize proposals after auto-confirm.");
            return spread;
        }
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
