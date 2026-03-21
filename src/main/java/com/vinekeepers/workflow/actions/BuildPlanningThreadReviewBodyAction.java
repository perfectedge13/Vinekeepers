package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.planreview.PlanningArtifactTexts;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds a thread-safe summary of drafted planning artifacts for pre-approval visibility.
 * Reads canonical artifact section values (same paths as proposal auto-apply).
 */
public final class BuildPlanningThreadReviewBodyAction implements com.vinekeepers.workflow.WorkflowAction {

    /** Max characters for the combined body (leave room for wrapper text in Discord 2000 limit). */
    private static final int TOTAL_CAP = 1900;
    /** Per-section budget before truncation. */
    private static final int SECTION_SOFT_MAX = 380;

    private final FeaturePlanStateStore planStore;

    public BuildPlanningThreadReviewBodyAction(FeaturePlanStateStore planStore) {
        this.planStore = planStore;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("planningThreadReviewBuildError", "");
        spread.put("planningThreadReviewBody", "");
        if (planStore == null) {
            spread.put("planningThreadReviewBuildError", "FeaturePlanStateStore not available.");
            return spread;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            spread.put("planningThreadReviewBuildError", "Missing contextId.");
            return spread;
        }
        FeaturePlanState plan = planStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            spread.put("planningThreadReviewBuildError", "No FeaturePlanState for context.");
            return spread;
        }

        String request = firstNonBlank(plan.getInitialRequest(), getString(state, "codeChange"));
        String repo = firstNonBlank(plan.getRepoRef(), getString(state, "project"));

        String featureSummary = truncate(PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "feature_summary"), SECTION_SOFT_MAX);
        String scope = truncate(PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "scope_summary"), SECTION_SOFT_MAX);
        String stories = truncate(PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "user_stories"), SECTION_SOFT_MAX);
        String acceptance = truncate(PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "acceptance_criteria"), SECTION_SOFT_MAX);
        String planBody = truncate(PlanningArtifactTexts.artifactField(plan, "overall_plan", "outline", "plan_body"), SECTION_SOFT_MAX);
        String validation = truncate(PlanningArtifactTexts.artifactField(plan, "validation_plan", "checks", "validation_notes"), SECTION_SOFT_MAX);
        String context = truncate(PlanningArtifactTexts.artifactField(plan, "project_context", "context", "context_summary"), SECTION_SOFT_MAX);
        String arch = truncate(PlanningArtifactTexts.artifactField(plan, "architecture_notes", "impact", "architecture_summary"), SECTION_SOFT_MAX);
        String comps = truncate(PlanningArtifactTexts.artifactField(plan, "architecture_notes", "impact", "components_impacted"), 200);
        String risks = truncate(PlanningArtifactTexts.artifactField(plan, "risk_register", "main", "risk_summary"), SECTION_SOFT_MAX);
        String openQ = truncate(PlanningArtifactTexts.artifactField(plan, "open_questions_block", "backlog", "open_questions"), SECTION_SOFT_MAX);
        String decision = truncate(
                PlanningArtifactTexts.firstRepeatableField(plan, "decision_log", "decisions", "decision_text"),
                SECTION_SOFT_MAX);

        StringBuilder sb = new StringBuilder();
        appendSection(sb, "**Request**", request);
        if (repo != null && !repo.isBlank()) {
            appendSection(sb, "**Repo**", repo);
        }
        appendSection(sb, "**Problem / goal**", featureSummary.isBlank() ? "(not drafted yet)" : featureSummary);
        appendSection(sb, "**Scope**", scope.isBlank() ? "(not drafted yet)" : scope);
        if (!stories.isBlank()) {
            appendSection(sb, "**User stories / scenarios**", stories);
        }
        appendSection(sb, "**Acceptance criteria**", acceptance.isBlank() ? "(not drafted yet)" : acceptance);
        if (!comps.isBlank() || !arch.isBlank()) {
            appendSection(sb, "**Architecture**",
                    (comps.isBlank() ? "" : "**Components:** " + comps + "\n\n")
                            + (arch.isBlank() ? "(not drafted yet)" : arch));
        }
        appendSection(sb, "**Implementation outline**", planBody.isBlank() ? "(not drafted yet)" : planBody);
        appendSection(sb, "**Risks / edge cases**", risks.isBlank() ? "(not drafted yet)" : risks);
        appendSection(sb, "**Open questions**", openQ.isBlank() ? "(not drafted yet)" : openQ);
        if (!decision.isBlank()) {
            appendSection(sb, "**Recorded decision**", decision);
        }
        appendSection(sb, "**Validation approach**", validation.isBlank() ? "(not drafted yet)" : validation);
        appendSection(sb, "**Project context**", context.isBlank() ? "(not drafted yet)" : context);

        String body = truncate(sb.toString().trim(), TOTAL_CAP);
        spread.put("planningThreadReviewBody", body);
        return spread;
    }

    private static void appendSection(StringBuilder sb, String heading, String content) {
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append(heading).append("\n");
        sb.append(content != null ? content : "");
    }

    private static String truncate(String s, int max) {
        if (s == null || s.isBlank()) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 1) + "…";
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return a != null ? a : (b != null ? b : "");
    }
}
