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
import java.util.Locale;
import java.util.Map;

/**
 * Consumes {@code discoveryAnswerRaw} for the current confirm target: OK accepts {@code proposalConfirmProposedValue},
 * otherwise treats the message as edited content. Pops {@code planningConfirmQueueJson} and drops the proposal from
 * {@code planningProposalsJson}.
 */
public final class ResolveProposalConfirmationAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStore;
    private final WorkProfileRegistry profileRegistry;

    public ResolveProposalConfirmationAction(FeaturePlanStateStore planStore, WorkProfileRegistry profileRegistry) {
        this.planStore = planStore;
        this.profileRegistry = profileRegistry;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("proposalResolveStatus", "SKIP");
        spread.put("planningHasConfirmPending", "false");
        if (planStore == null || profileRegistry == null) {
            spread.put("proposalResolveStatus", "NO_STORE");
            return spread;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            spread.put("proposalResolveStatus", "NO_CONTEXT");
            return spread;
        }
        List<String> queue;
        try {
            queue = PlanningProposalJson.parseStringList(
                    firstNonBlank(getString(bind, "planningConfirmQueueJson"), getString(state, "planningConfirmQueueJson")));
        } catch (JsonProcessingException e) {
            spread.put("proposalResolveStatus", "BAD_QUEUE_JSON");
            return spread;
        }
        if (queue.isEmpty()) {
            return spread;
        }
        String answer = firstNonBlank(getString(bind, "discoveryAnswerRaw"), getString(state, "discoveryAnswerRaw"));
        if (answer == null || answer.isBlank()) {
            spread.put("proposalResolveStatus", "EMPTY_ANSWER");
            spread.put("planningHasConfirmPending", "true");
            return spread;
        }
        String proposalId =
                firstNonBlank(getString(bind, "proposalConfirmProposalId"), getString(state, "proposalConfirmProposalId"));
        if (proposalId == null || proposalId.isBlank() || !proposalId.equals(queue.get(0))) {
            proposalId = queue.get(0);
        }
        List<PlanningProposal> proposals;
        try {
            proposals = PlanningProposalJson.parseList(
                    firstNonBlank(getString(bind, "planningProposalsJson"), getString(state, "planningProposalsJson")));
        } catch (JsonProcessingException e) {
            spread.put("proposalResolveStatus", "BAD_PROPOSALS_JSON");
            return spread;
        }
        PlanningProposal match = null;
        for (PlanningProposal p : proposals) {
            if (proposalId.equals(p.getProposalId())) {
                match = p;
                break;
            }
        }
        if (match == null) {
            spread.put("proposalResolveStatus", "MISSING_PROPOSAL");
            return spread;
        }
        String valueToStore;
        if (isOkToken(answer)) {
            valueToStore = firstNonBlank(
                    getString(bind, "proposalConfirmProposedValue"), getString(state, "proposalConfirmProposedValue"));
            if (valueToStore == null) {
                valueToStore = match.getProposedValue();
            }
        } else {
            valueToStore = answer.trim();
        }

        Map<String, Object> baseState = new LinkedHashMap<>();
        if (state != null) {
            baseState.putAll(state);
        }
        baseState.put("contextId", contextId);
        UpsertArtifactSectionDataAction upsert = new UpsertArtifactSectionDataAction(planStore, profileRegistry);
        upsert.run(
                event,
                baseState,
                Map.of(
                        "artifactId", match.getArtifactId(),
                        "sectionId", match.getSectionId(),
                        "mode", "replace",
                        "data", Map.of(match.getFieldId(), valueToStore)));

        List<String> newQueue = new ArrayList<>(queue.subList(1, queue.size()));
        List<PlanningProposal> newProposals = new ArrayList<>();
        for (PlanningProposal p : proposals) {
            if (!p.getProposalId().equals(match.getProposalId())) {
                newProposals.add(p);
            }
        }
        try {
            spread.put("planningConfirmQueueJson", PlanningProposalJson.stringListToJson(newQueue));
            spread.put("planningProposalsJson", PlanningProposalJson.toJson(newProposals));
            spread.put("planningHasConfirmPending", newQueue.isEmpty() ? "false" : "true");
        } catch (JsonProcessingException e) {
            spread.put("proposalResolveStatus", "SERIALIZE_FAILED");
            return spread;
        }
        spread.put("proposalResolveStatus", "OK");
        return spread;
    }

    private static boolean isOkToken(String answer) {
        String t = answer.trim();
        if (t.isEmpty()) {
            return false;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        return lower.equals("ok")
                || lower.equals("yes")
                || lower.equals("y")
                || lower.equals("accept");
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
