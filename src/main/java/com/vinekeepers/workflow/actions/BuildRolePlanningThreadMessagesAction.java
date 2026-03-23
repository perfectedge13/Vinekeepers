package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.planreview.PlanningArtifactTexts;
import com.vinekeepers.workflow.planreview.PlanningThreadPacketFormatter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds thread copy for architect / auditor / scribe from canonical planning artifacts.
 */
public final class BuildRolePlanningThreadMessagesAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStateStore;

    public BuildRolePlanningThreadMessagesAction(FeaturePlanStateStore planStateStore) {
        this.planStateStore = planStateStore;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("architectThreadMessage", "");
        out.put("auditorThreadMessage", "");
        out.put("scribeThreadMessage", "");
        if (planStateStore == null) {
            return out;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            return out;
        }
        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            return out;
        }
        String outline = PlanningArtifactTexts.artifactField(plan, "overall_plan", "outline", "plan_body");
        String decisions = PlanningArtifactTexts.allRepeatableFieldLines(plan, "decision_log", "decisions", "decision_text");
        String validation = PlanningArtifactTexts.artifactField(plan, "validation_plan", "checks", "validation_notes");
        String featureSummary = PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "feature_summary");
        String acceptance = PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "acceptance_criteria");
        String scope = PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "scope_summary");
        String stories = PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "user_stories");
        String contextSummary = PlanningArtifactTexts.artifactField(plan, "project_context", "context", "context_summary");
        String arch = PlanningArtifactTexts.artifactField(plan, "architecture_notes", "impact", "architecture_summary");
        String comps = PlanningArtifactTexts.artifactField(plan, "architecture_notes", "impact", "components_impacted");
        String risks = PlanningArtifactTexts.artifactField(plan, "risk_register", "main", "risk_summary");
        String openQ = PlanningArtifactTexts.effectiveOpenQuestions(plan);
        int issueCount = plan.getIssues() != null ? plan.getIssues().size() : 0;

        out.put("architectThreadMessage", buildArchitect(outline, decisions, arch, comps));
        out.put("auditorThreadMessage", buildAuditor(validation, issueCount, risks, openQ));
        out.put("scribeThreadMessage", buildScribe(featureSummary, acceptance, scope, stories, contextSummary));
        return out;
    }

    private static String buildArchitect(String outline, String decisions, String arch, String comps) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Architect — structure & boundaries**\n");
        if (comps != null && !comps.isBlank()) {
            sb.append("**Components:** ").append(comps.trim()).append("\n\n");
        }
        if (arch != null && !arch.isBlank()) {
            sb.append(arch.trim()).append("\n\n");
        }
        if (outline != null && !outline.isBlank()) {
            sb.append(outline.trim());
        } else {
            sb.append("_No plan outline recorded yet._");
        }
        if (decisions != null && !decisions.isBlank()) {
            String decTable = PlanningThreadPacketFormatter.bulletLinesAsMarkdownTable("Decision", decisions);
            sb.append("\n\n**Decisions**\n");
            if (!decTable.isBlank()) {
                sb.append(decTable);
            } else {
                sb.append(decisions.trim());
            }
        }
        return sb.toString();
    }

    private static String buildAuditor(String validation, int issueCount, String risks, String openQ) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Auditor — validation & risks**\n");
        if (risks != null && !risks.isBlank()) {
            String riskTable = PlanningThreadPacketFormatter.bulletLinesAsMarkdownTable("Risk", risks);
            sb.append("**Risk register**\n");
            if (!riskTable.isBlank()) {
                sb.append(riskTable).append("\n\n");
            } else {
                sb.append(risks.trim()).append("\n\n");
            }
        }
        if (openQ != null && !openQ.isBlank()) {
            String oqTable = PlanningThreadPacketFormatter.bulletLinesAsMarkdownTable("Question", openQ);
            sb.append("**Open questions**\n");
            if (!oqTable.isBlank()) {
                sb.append(oqTable).append("\n\n");
            } else {
                sb.append(openQ.trim()).append("\n\n");
            }
        }
        if (validation != null && !validation.isBlank()) {
            sb.append("**Validation**\n").append(validation.trim());
        } else {
            sb.append("_No validation approach recorded yet._");
        }
        sb.append("\n\n**Tracked issues on the plan:** ").append(issueCount);
        return sb.toString();
    }

    private static String buildScribe(
            String featureSummary,
            String acceptance,
            String scope,
            String stories,
            String contextSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Scribe — requirements & context**\n");
        sb.append("**Summary:** ");
        sb.append(featureSummary != null && !featureSummary.isBlank() ? featureSummary.trim() : "_None._");
        sb.append("\n\n**Scope:** ");
        sb.append(scope != null && !scope.isBlank() ? scope.trim() : "_Not specified._");
        if (stories != null && !stories.isBlank()) {
            String stTable = PlanningThreadPacketFormatter.bulletLinesAsMarkdownTable("Story / scenario", stories);
            sb.append("\n\n**User stories / scenarios:**\n");
            if (!stTable.isBlank()) {
                sb.append(stTable);
            } else {
                sb.append(stories.trim());
            }
        }
        sb.append("\n\n**Acceptance criteria:** ");
        if (acceptance != null && !acceptance.isBlank()) {
            String acTable = PlanningThreadPacketFormatter.bulletLinesAsMarkdownTable("Criterion", acceptance);
            if (!acTable.isBlank()) {
                sb.append(acTable);
            } else {
                sb.append(acceptance.trim());
            }
        } else {
            sb.append("_Not specified._");
        }
        if (contextSummary != null && !contextSummary.isBlank()) {
            sb.append("\n\n**Context:**\n").append(contextSummary.trim());
        }
        return sb.toString();
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
