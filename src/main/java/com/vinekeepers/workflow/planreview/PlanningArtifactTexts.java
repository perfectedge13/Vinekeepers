package com.vinekeepers.workflow.planreview;

import com.vinekeepers.profile.ArtifactState;
import com.vinekeepers.profile.SectionState;
import com.vinekeepers.state.planning.FeaturePlanState;

import java.util.List;
import java.util.Map;

/**
 * Reads canonical artifact section values (same paths as proposal auto-apply / thread review body).
 */
public final class PlanningArtifactTexts {

    private PlanningArtifactTexts() {}

    /** First row of a repeatable section (e.g. decision log). */
    public static String firstRepeatableField(FeaturePlanState plan, String artifactId, String sectionId, String fieldId) {
        if (plan == null) {
            return "";
        }
        ArtifactState art = plan.getArtifacts().get(artifactId);
        if (art == null) {
            return "";
        }
        SectionState sec = art.getSectionsById().get(sectionId);
        if (sec == null) {
            return "";
        }
        List<Map<String, Object>> entries = sec.getEntries();
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        Object v = entries.get(0).get(fieldId);
        return v != null ? v.toString().trim() : "";
    }

    public static String artifactField(FeaturePlanState plan, String artifactId, String sectionId, String fieldId) {
        if (plan == null) {
            return "";
        }
        ArtifactState art = plan.getArtifacts().get(artifactId);
        if (art == null) {
            return "";
        }
        SectionState sec = art.getSectionsById().get(sectionId);
        if (sec == null) {
            return "";
        }
        Object v = sec.getValues().get(fieldId);
        return v != null ? v.toString().trim() : "";
    }
}
