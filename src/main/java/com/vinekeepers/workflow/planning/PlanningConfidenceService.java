package com.vinekeepers.workflow.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanAssumptionStatus;
import com.vinekeepers.state.planning.PlanConfidence;
import com.vinekeepers.state.planning.PlanGovernanceSeverity;
import com.vinekeepers.state.planning.PlanIssue;
import com.vinekeepers.state.planning.PlanIssueStatus;
import com.vinekeepers.state.workflow.UnresolvedItem;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.state.workflow.UnresolvedItemStatus;
import com.vinekeepers.workflow.planreview.PlanningArtifactTexts;

import java.util.Locale;

/**
 * Numeric planning confidence inputs (repo grounding, evidence JSON). No gap selection or clarification policy.
 */
public final class PlanningConfidenceService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private PlanningConfidenceService() {}

    /**
     * Cycle clarification confidence in [0,1] for spread keys. Uses persisted {@link PlanConfidence} when present; otherwise
     * derives from depth, parse health, repo grounding, and material unknowns (no gap selection).
     */
    public static double clarificationCycleConfidenceScore(
            FeaturePlanState plan,
            UnresolvedItemLedger ledger,
            ClarificationProjection ranked,
            String planningRepoEvidenceJson,
            boolean userInputRequired,
            boolean depthOk,
            boolean structuredParseFailed) {
        PlanConfidence stored = plan != null ? plan.getPlanConfidence() : null;
        if (stored != null && stored.getConfidenceScore() >= 0) {
            return clamp01(stored.getConfidenceScore());
        }
        double score = 0.18;
        if (depthOk) {
            score += 0.24;
        }
        if (!structuredParseFailed) {
            score += 0.08;
        }
        score += 0.28 * repoEvidenceGroundingScore(plan, planningRepoEvidenceJson);
        score += Math.min(0.22, clarificationKnownFactCount(plan) * 0.012);
        score -= Math.min(
                0.55,
                clarificationMaterialUnknownCount(plan, ledger, ranked, userInputRequired, structuredParseFailed) * 0.14);
        return clamp01(score);
    }

    private static double clamp01(double score) {
        return Math.max(0.0, Math.min(1.0, score));
    }

    /** Score in [0,1] from plan materialization + structured repo evidence JSON. */
    public static double repoEvidenceGroundingScore(FeaturePlanState plan, String planningRepoEvidenceJson) {
        double s = 0.0;
        if (plan != null) {
            if (plan.getRepoLocalPath() != null && !plan.getRepoLocalPath().isBlank()) {
                s += 0.28;
            }
            String st =
                    plan.getRepoWorkspaceStatus() != null ? plan.getRepoWorkspaceStatus().trim().toUpperCase(Locale.ROOT) : "";
            if ("MATERIALIZED".equals(st) || "RESOLVED_LOCAL".equals(st)) {
                s += 0.22;
            }
            String ex = PlanningArtifactTexts.artifactField(plan, "request_exploration", "analysis", "exploration_body");
            if (ex != null && ex.trim().length() >= 180) {
                s += 0.18;
            }
            String comps =
                    PlanningArtifactTexts.artifactField(plan, "architecture_notes", "impact", "components_impacted");
            if (comps != null && comps.trim().length() >= 40) {
                s += 0.14;
            }
        }
        s += jsonEvidenceBonus(planningRepoEvidenceJson);
        return Math.min(1.0, s);
    }

    private static double jsonEvidenceBonus(String planningRepoEvidenceJson) {
        if (planningRepoEvidenceJson == null || planningRepoEvidenceJson.isBlank()) {
            return 0.0;
        }
        try {
            JsonNode n = JSON.readTree(planningRepoEvidenceJson.trim());
            double b = 0.0;
            if (n.path("localPathPresent").asBoolean(false)) {
                b += 0.08;
            }
            String ws = n.path("workspaceStatus").asText("");
            if (ws.toUpperCase(Locale.ROOT).contains("MATERIALIZED")
                    || ws.toUpperCase(Locale.ROOT).contains("RESOLVED")) {
                b += 0.06;
            }
            if (n.path("blockingIssues").isIntegralNumber() && n.path("blockingIssues").asInt() == 0) {
                b += 0.04;
            }
            return b;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /** Persisted factor snapshot for a planning cycle (no policy). */
    public static PlanningConfidenceBreakdown cycleBreakdown(
            FeaturePlanState plan,
            double clarificationConfidenceScore,
            boolean depthOk,
            boolean structuredParseFailed,
            UnresolvedItemLedger ledger,
            ClarificationProjection ranked,
            boolean userInputRequired,
            String planningRepoEvidenceJson) {
        return new PlanningConfidenceBreakdown(
                repoEvidenceGroundingScore(plan, planningRepoEvidenceJson),
                clarificationConfidenceScore,
                depthOk,
                !structuredParseFailed,
                clarificationKnownFactCount(plan),
                clarificationMaterialUnknownCount(plan, ledger, ranked, userInputRequired, structuredParseFailed),
                "");
    }

    private static int clarificationKnownFactCount(FeaturePlanState plan) {
        if (plan == null) {
            return 0;
        }
        int known = 0;
        known += plan.getRequirements().size();
        known += plan.getDecisions().size();
        known += plan.getValidationNotes().size();
        known += plan.getRisks().size();
        String featureSummary = PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "feature_summary");
        if (featureSummary != null && featureSummary.trim().length() >= 24) {
            known += 2;
        }
        String exploration = PlanningArtifactTexts.artifactField(plan, "request_exploration", "analysis", "exploration_body");
        if (exploration != null && exploration.trim().length() >= 120) {
            known += 2;
        }
        return known;
    }

    private static int clarificationMaterialUnknownCount(
            FeaturePlanState plan,
            UnresolvedItemLedger ledger,
            ClarificationProjection ranked,
            boolean userInputRequired,
            boolean structuredParseFailed) {
        int unknowns = 0;
        if (userInputRequired) {
            unknowns++;
        }
        if (structuredParseFailed) {
            unknowns++;
        }
        if (ranked != null && ranked.userInputRequired() && ranked.questionText() != null && !ranked.questionText().isBlank()) {
            unknowns++;
        }
        if (ledger != null) {
            for (UnresolvedItem it : ledger.items()) {
                if (it.getStatus() == UnresolvedItemStatus.OPEN
                        && "planning_clarification".equals(it.getSource().get("channel"))) {
                    unknowns++;
                }
            }
        }
        if (plan == null) {
            return unknowns;
        }
        for (String q : plan.getUnresolvedQuestions()) {
            if (q != null && !q.isBlank()) {
                unknowns++;
            }
        }
        for (PlanIssue issue : plan.getIssues()) {
            if (!PlanIssueStatus.OPEN.equalsIgnoreCase(issue.getStatus())) {
                continue;
            }
            if (issue.isBlocking()) {
                unknowns++;
            }
            String blob =
                    (issue.getTitle() != null ? issue.getTitle() : "")
                            + " "
                            + (issue.getDetail() != null ? issue.getDetail() : "");
            if (blob.toLowerCase(Locale.ROOT).contains("contradiction")) {
                unknowns += 2;
            }
        }
        for (var assumption : plan.getAssumptions()) {
            if (PlanAssumptionStatus.OPEN.equalsIgnoreCase(assumption.getStatus())
                    && PlanGovernanceSeverity.HIGH.equalsIgnoreCase(assumption.getSeverity())) {
                unknowns++;
            }
        }
        return unknowns;
    }
}
