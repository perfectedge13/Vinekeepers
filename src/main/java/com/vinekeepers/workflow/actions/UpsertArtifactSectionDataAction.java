package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.profile.ArtifactState;
import com.vinekeepers.profile.BindPlaceholderResolver;
import com.vinekeepers.profile.SectionDefinition;
import com.vinekeepers.profile.SectionState;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic profile-driven write: upsert data for an artifact section (replace or append for repeatable sections).
 */
public final class UpsertArtifactSectionDataAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStateStore;
    private final WorkProfileRegistry workProfileRegistry;

    public UpsertArtifactSectionDataAction(FeaturePlanStateStore planStateStore, WorkProfileRegistry workProfileRegistry) {
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
            return "Missing contextId for upsert_artifact_section_data.";
        }
        String artifactId = firstNonBlank(getString(bind, "artifactId"), getString(state, "artifactId"));
        String sectionId = firstNonBlank(getString(bind, "sectionId"), getString(state, "sectionId"));
        if (artifactId == null || artifactId.isBlank() || sectionId == null || sectionId.isBlank()) {
            return "Missing artifactId or sectionId for upsert_artifact_section_data.";
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
        SectionDefinition secDef = profile.findSection(artifactId, sectionId).orElse(null);
        if (secDef == null) {
            return "Unknown artifact/section for profile: " + artifactId + " / " + sectionId;
        }

        Map<String, Object> lookup = mergedLookup(state, bind);
        Object rawData = bind != null ? bind.get("data") : null;
        if (rawData == null && state != null) {
            rawData = state.get("data");
        }
        Object resolvedData = BindPlaceholderResolver.resolveDeep(BindPlaceholderResolver.asStringKeyMap(rawData), lookup);
        Map<String, Object> dataMap =
                resolvedData instanceof Map<?, ?> rm ? BindPlaceholderResolver.asStringKeyMap(rm) : Map.of();

        String mode = firstNonBlank(getString(bind, "mode"), getString(state, "mode"));
        if (mode == null || mode.isBlank()) {
            mode = "replace";
        }
        mode = mode.trim().toLowerCase();

        ArtifactState art = plan.getArtifacts().get(artifactId);
        if (art == null) {
            return "Plan has no artifact state for: " + artifactId;
        }
        SectionState current = art.getSectionsById().get(sectionId);
        if (current == null) {
            return "Plan has no section state for: " + sectionId;
        }

        SectionState nextSection = buildNextSection(current, secDef, dataMap, mode);
        ArtifactState nextArt = art.withSection(sectionId, nextSection);
        Map<String, ArtifactState> nextArtifacts = new LinkedHashMap<>(plan.getArtifacts());
        nextArtifacts.put(artifactId, nextArt);
        planStateStore.update(plan.withArtifacts(nextArtifacts));
        return "OK";
    }

    private static SectionState buildNextSection(
            SectionState current,
            SectionDefinition secDef,
            Map<String, Object> dataMap,
            String mode) {
        if (secDef.isRepeatable()) {
            List<Map<String, Object>> entries = new ArrayList<>(current.getEntries());
            if ("append".equals(mode)) {
                entries.add(new LinkedHashMap<>(dataMap));
            } else {
                entries.clear();
                entries.add(new LinkedHashMap<>(dataMap));
            }
            return new SectionState(current.getSectionId(), SectionState.STATUS_DRAFT, Map.of(), entries);
        }
        Map<String, Object> values = new LinkedHashMap<>(current.getValues());
        if ("append".equals(mode)) {
            for (Map.Entry<String, Object> e : dataMap.entrySet()) {
                Object v = values.get(e.getKey());
                if (v == null || (v instanceof String s && s.isBlank())) {
                    values.put(e.getKey(), e.getValue());
                } else if (v instanceof String vs && e.getValue() instanceof String es) {
                    values.put(e.getKey(), vs + es);
                } else {
                    values.put(e.getKey(), e.getValue());
                }
            }
        } else {
            values.putAll(dataMap);
        }
        return new SectionState(current.getSectionId(), SectionState.STATUS_DRAFT, values, List.of());
    }

    private static Map<String, Object> mergedLookup(Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (state != null) {
            m.putAll(state);
        }
        if (bind != null) {
            m.putAll(bind);
        }
        return m;
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
