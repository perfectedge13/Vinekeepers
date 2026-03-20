package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.profile.ArtifactDefinition;
import com.vinekeepers.profile.ArtifactState;
import com.vinekeepers.profile.FieldDefinition;
import com.vinekeepers.profile.SectionDefinition;
import com.vinekeepers.profile.SectionState;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.planning.PlanningPromptFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Returns a concise summary of required profile fields that are still missing in {@link FeaturePlanState} artifacts.
 */
public final class GetProfileMissingFieldsAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStateStore;
    private final WorkProfileRegistry workProfileRegistry;

    public GetProfileMissingFieldsAction(FeaturePlanStateStore planStateStore, WorkProfileRegistry workProfileRegistry) {
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
            return "Missing contextId for get_profile_missing_fields.";
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

        List<String> missing = new ArrayList<>();
        for (ArtifactDefinition art : profile.getArtifactsById().values()) {
            ArtifactState artState = plan.getArtifacts().get(art.getArtifactId());
            for (SectionDefinition sec : art.getSections()) {
                SectionState secState =
                        artState != null ? artState.getSectionsById().get(sec.getSectionId()) : null;
                if (sec.isRepeatable()) {
                    if (sec.isRequired()) {
                        if (secState == null || secState.getEntries().isEmpty()) {
                            missing.add(art.getArtifactId() + "." + sec.getSectionId() + " (repeatable section empty)");
                        }
                    }
                    if (secState != null) {
                        for (int i = 0; i < secState.getEntries().size(); i++) {
                            Map<String, Object> row = secState.getEntries().get(i);
                            for (FieldDefinition f : sec.getFields()) {
                                if (f.isRequired() && isMissing(row.get(f.getFieldId()))) {
                                    missing.add(art.getArtifactId() + "." + sec.getSectionId() + "[" + i + "]." + f.getFieldId());
                                }
                            }
                        }
                    }
                } else {
                    if (sec.isRequired() && secState == null) {
                        missing.add(art.getArtifactId() + "." + sec.getSectionId() + " (section missing)");
                    }
                    Map<String, Object> values = secState != null ? secState.getValues() : Map.of();
                    for (FieldDefinition f : sec.getFields()) {
                        if (f.isRequired() && isMissing(values.get(f.getFieldId()))) {
                            missing.add(art.getArtifactId() + "." + sec.getSectionId() + "." + f.getFieldId());
                        }
                    }
                }
            }
        }
        if (missing.isEmpty()) {
            return "All required profile fields present.";
        }
        return PlanningPromptFormatter.formatMissingRequiredSummary(profile, missing);
    }

    private static boolean isMissing(Object v) {
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
