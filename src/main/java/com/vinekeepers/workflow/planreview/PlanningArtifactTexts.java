package com.vinekeepers.workflow.planreview;

import com.vinekeepers.profile.ArtifactState;
import com.vinekeepers.profile.SectionState;
import com.vinekeepers.state.planning.FeaturePlanState;

/**
 * Reads canonical artifact section values (same paths as proposal auto-apply / thread review body).
 */
public final class PlanningArtifactTexts {

    private PlanningArtifactTexts() {}

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
