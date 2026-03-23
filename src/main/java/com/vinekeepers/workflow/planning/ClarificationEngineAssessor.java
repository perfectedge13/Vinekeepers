package com.vinekeepers.workflow.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.profile.CoordinatorClarificationEnginePolicy;
import com.vinekeepers.profile.CoordinatorClarificationGapRule;
import com.vinekeepers.profile.CoordinatorClarificationSettings;
import com.vinekeepers.state.planning.ClarificationResolutionDecision;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.workflow.planreview.PlanningArtifactTexts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Post-draft clarification assessor: ranks coordinator gaps with repo evidence, applies budget/thresholds, and chooses
 * {@link ClarificationResolutionDecision} per gap before a single question is surfaced.
 */
public final class ClarificationEngineAssessor {

    private static final ObjectMapper JSON = new ObjectMapper();

    public record AssessedGap(
            CoordinatorClarificationGapEvaluator.OpenGap gap,
            ClarificationResolutionDecision decision,
            String assumptionToRecord,
            double rankScore) {}

    private ClarificationEngineAssessor() {}

    /**
     * When {@code semanticGapsAllowed} is false, semantic coordinator gaps are not evaluated (workspace / hard path only
     * applies elsewhere).
     */
    public static List<AssessedGap> assessCanonicalGaps(
            FeaturePlanState plan,
            CoordinatorClarificationSettings settings,
            List<String> llmHints,
            boolean semanticGapsAllowed,
            String planningRepoEvidenceJson,
            boolean critiqueBlockingFollowUp) {
        if (plan == null || settings == null || !settings.isCanonicalV1() || !semanticGapsAllowed) {
            return List.of();
        }
        List<CoordinatorClarificationGapEvaluator.OpenGap> open =
                CoordinatorClarificationGapEvaluator.evaluateOpenGaps(plan, settings, llmHints);
        if (open.isEmpty()) {
            return List.of();
        }
        CoordinatorClarificationEnginePolicy pol = settings.getEnginePolicy();
        double evidence = repoEvidenceGroundingScore(plan, planningRepoEvidenceJson);
        double askThreshold = pol.getRepoEvidenceAskThreshold();
        if (critiqueBlockingFollowUp && pol.isAllowClarificationAfterCritique()) {
            askThreshold = Math.max(0.15, askThreshold - 0.12);
        }
        int turns = plan.getClarificationTurnsCompleted();
        int budget = pol.getMaxClarificationTurns();
        boolean budgetExhausted = budget > 0 && turns >= budget;

        List<AssessedGap> assessed = new ArrayList<>();
        for (CoordinatorClarificationGapEvaluator.OpenGap g : open) {
            assessed.add(
                    assessOneGap(
                            g,
                            settings,
                            evidence,
                            askThreshold,
                            pol,
                            budgetExhausted,
                            gapAskCount(plan, g.gapId())));
        }
        assessed.sort(Comparator.comparingDouble(AssessedGap::rankScore).reversed());
        return List.copyOf(assessed);
    }

    private static AssessedGap assessOneGap(
            CoordinatorClarificationGapEvaluator.OpenGap g,
            CoordinatorClarificationSettings settings,
            double evidenceScore,
            double askThreshold,
            CoordinatorClarificationEnginePolicy pol,
            boolean budgetExhausted,
            int gapAskCount) {
        CoordinatorClarificationGapRule rule = settings.findGapRule(g.gapId()).orElse(null);
        double rank = rankGap(g, evidenceScore, rule);
        boolean gapBudgetExhausted =
                pol.getMaxClarificationTurnsPerGap() > 0 && gapAskCount >= pol.getMaxClarificationTurnsPerGap();
        if (g.blocking()) {
            if (budgetExhausted || gapBudgetExhausted) {
                return new AssessedGap(
                        g,
                        ClarificationResolutionDecision.BLOCK_AS_UNIMPLEMENTABLE,
                        "Coordinator clarification budget exhausted while a blocking planning gap remains open ("
                                + g.gapId()
                                + ").",
                        rank);
            }
            return new AssessedGap(g, ClarificationResolutionDecision.ASK_USER, "", rank);
        }
        if (budgetExhausted || gapBudgetExhausted) {
            if (pol.isAllowAssumeAndContinue()) {
                return new AssessedGap(
                        g,
                        ClarificationResolutionDecision.ASSUME_AND_CONTINUE,
                        defaultAssumptionLine(g),
                        rank);
            }
            return new AssessedGap(g, ClarificationResolutionDecision.LOW_PRIORITY_DEFER, "", rank);
        }
        if (pol.isAllowAssumeAndContinue() && evidenceScore >= askThreshold) {
            return new AssessedGap(
                    g,
                    ClarificationResolutionDecision.ASSUME_AND_CONTINUE,
                    defaultAssumptionLine(g),
                    rank);
        }
        if (pol.isAllowAssumeAndContinue() && rank < pol.getClarificationAskPriorityThreshold()) {
            return new AssessedGap(
                    g,
                    ClarificationResolutionDecision.ASSUME_AND_CONTINUE,
                    defaultAssumptionLine(g),
                    rank);
        }
        return new AssessedGap(g, ClarificationResolutionDecision.ASK_USER, "", rank);
    }

    private static String defaultAssumptionLine(CoordinatorClarificationGapEvaluator.OpenGap g) {
        return "Assumed default for coordinator gap `"
                + g.gapId()
                + "` after repo-grounded draft — confirm or correct in-thread if this is wrong.";
    }

    private static double rankGap(
            CoordinatorClarificationGapEvaluator.OpenGap g,
            double evidenceScore,
            CoordinatorClarificationGapRule rule) {
        double score = evidenceScore;
        if (g.blocking()) {
            score += 0.35;
        }
        if (rule != null && rule.isBlocking()) {
            score += 0.05;
        }
        String q = g.questionText() != null ? g.questionText() : "";
        score += Math.min(0.12, q.length() / 4000.0);
        return score;
    }

    private static int gapAskCount(FeaturePlanState plan, String gapId) {
        if (plan == null || gapId == null || gapId.isBlank() || plan.getClarificationOutcomeHistory() == null) {
            return 0;
        }
        String prefix = "ask:" + gapId.trim() + ":";
        int count = 0;
        for (String row : plan.getClarificationOutcomeHistory()) {
            if (row != null && row.startsWith(prefix)) {
                count++;
            }
        }
        return count;
    }

    public static double repoEvidenceGroundingScore(FeaturePlanState plan, String planningRepoEvidenceJson) {
        double s = 0.0;
        if (plan != null) {
            if (plan.getRepoLocalPath() != null && !plan.getRepoLocalPath().isBlank()) {
                s += 0.28;
            }
            String st = plan.getRepoWorkspaceStatus() != null ? plan.getRepoWorkspaceStatus().trim().toUpperCase(Locale.ROOT) : "";
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
}
