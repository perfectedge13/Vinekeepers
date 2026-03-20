package com.vinekeepers.workflow.actions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.ArtifactDefinition;
import com.vinekeepers.profile.FieldDefinition;
import com.vinekeepers.profile.SectionDefinition;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.PlanningProposal;
import com.vinekeepers.workflow.planning.PlanningProposalJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prepares plain-English confirmation prompt and apply-target keys for the head of {@code planningConfirmQueueJson}.
 */
public final class BuildProposalConfirmPromptAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStore;
    private final WorkProfileRegistry profileRegistry;

    public BuildProposalConfirmPromptAction(FeaturePlanStateStore planStore, WorkProfileRegistry profileRegistry) {
        this.planStore = planStore;
        this.profileRegistry = profileRegistry;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("proposalConfirmPrompt", "");
        spread.put("proposalConfirmProposalId", "");
        spread.put("proposalConfirmArtifactId", "");
        spread.put("proposalConfirmSectionId", "");
        spread.put("proposalConfirmFieldId", "");
        spread.put("proposalConfirmProposedValue", "");
        spread.put("planningHasConfirmPending", "false");
        if (planStore == null || profileRegistry == null) {
            return spread;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            return spread;
        }
        List<String> queue;
        List<PlanningProposal> proposals;
        try {
            queue = PlanningProposalJson.parseStringList(
                    firstNonBlank(getString(bind, "planningConfirmQueueJson"), getString(state, "planningConfirmQueueJson")));
            proposals = PlanningProposalJson.parseList(
                    firstNonBlank(getString(bind, "planningProposalsJson"), getString(state, "planningProposalsJson")));
        } catch (JsonProcessingException e) {
            return spread;
        }
        if (queue.isEmpty()) {
            return spread;
        }
        List<String> q = new ArrayList<>(queue);
        PlanningProposal match = null;
        String headId = null;
        while (!q.isEmpty()) {
            headId = q.get(0);
            for (PlanningProposal p : proposals) {
                if (headId.equals(p.getProposalId())) {
                    match = p;
                    break;
                }
            }
            if (match != null) {
                break;
            }
            q.remove(0);
        }
        try {
            spread.put("planningConfirmQueueJson", PlanningProposalJson.stringListToJson(q));
        } catch (JsonProcessingException e) {
            return spread;
        }
        if (match == null) {
            spread.put("planningHasConfirmPending", "false");
            return spread;
        }
        FeaturePlanState plan = planStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            return spread;
        }
        String profileId = plan.getProfileId();
        WorkProfileDefinition profile =
                profileId != null && !profileId.isBlank() ? profileRegistry.get(profileId).orElse(null) : null;
        String labelLine = friendlyLabel(profile, match.getArtifactId(), match.getSectionId(), match.getFieldId());

        StringBuilder sb = new StringBuilder();
        sb.append(labelLine).append("\n\n");
        if (match.getReasoningSummary() != null && !match.getReasoningSummary().isBlank()) {
            sb.append("_").append(match.getReasoningSummary().trim()).append("_\n\n");
        }
        sb.append("```\n");
        sb.append(match.getProposedValue() != null ? match.getProposedValue().trim() : "");
        sb.append("\n```\n\n");
        sb.append("Reply **OK** to accept this draft, or send your **edited** text in one message.");

        spread.put("proposalConfirmPrompt", sb.toString());
        spread.put("proposalConfirmProposalId", match.getProposalId());
        spread.put("proposalConfirmArtifactId", match.getArtifactId());
        spread.put("proposalConfirmSectionId", match.getSectionId());
        spread.put("proposalConfirmFieldId", match.getFieldId());
        spread.put("proposalConfirmProposedValue", match.getProposedValue());
        spread.put("planningHasConfirmPending", "true");
        return spread;
    }

    private static String friendlyLabel(
            WorkProfileDefinition profile, String artifactId, String sectionId, String fieldId) {
        if (profile == null) {
            return "**Confirm planning draft**";
        }
        ArtifactDefinition art = profile.getArtifactsById().get(artifactId);
        String artTitle = art != null && art.getTitle() != null && !art.getTitle().isBlank()
                ? art.getTitle()
                : artifactId;
        SectionDefinition sec = null;
        if (art != null) {
            sec = art.getSections().stream()
                    .filter(s -> sectionId.equals(s.getSectionId()))
                    .findFirst()
                    .orElse(null);
        }
        String secTitle = sec != null && sec.getTitle() != null && !sec.getTitle().isBlank()
                ? sec.getTitle()
                : sectionId;
        FieldDefinition fld = null;
        if (sec != null) {
            fld = sec.getFields().stream()
                    .filter(f -> fieldId.equals(f.getFieldId()))
                    .findFirst()
                    .orElse(null);
        }
        String fieldLabel = fld != null && fld.getLabel() != null && !fld.getLabel().isBlank()
                ? fld.getLabel()
                : fieldId;
        return "**" + fieldLabel + "** — _" + secTitle + "_ (" + artTitle + ")";
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
