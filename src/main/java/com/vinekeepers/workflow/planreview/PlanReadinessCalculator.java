package com.vinekeepers.workflow.planreview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.profile.ArtifactState;
import com.vinekeepers.profile.SectionState;
import com.vinekeepers.state.planning.DiscoveryGap;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanAssumptionStatus;
import com.vinekeepers.state.planning.PlanConfidence;
import com.vinekeepers.state.planning.PlanCritiqueFinding;
import com.vinekeepers.state.planning.PlanCritiqueRubricScores;
import com.vinekeepers.state.planning.PlanGovernanceSeverity;
import com.vinekeepers.state.planning.PlanIssueStatus;
import com.vinekeepers.state.planning.PlanReadinessStatus;
import com.vinekeepers.state.planning.PlanningIntakeStage;
import com.vinekeepers.state.planning.SolutionOutline;
import com.vinekeepers.workflow.planning.PlanningPostDraftGovernor;
import com.vinekeepers.workflow.planning.PlanningReadinessSpread;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Evidence-based readiness and confidence derived from gaps, critique, rubric, and governance tallies.
 */
public final class PlanReadinessCalculator {

    /** Launch approval: single source of truth for {@link PlanningApprovalGateSupport}. */
    public static final double APPROVAL_CONFIDENCE_THRESHOLD = 0.85;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_MATERIAL_UNKNOWN_LABELS = 12;

    private PlanReadinessCalculator() {}

    public static PlanConfidence evaluate(
            FeaturePlanState plan,
            List<DiscoveryGap> gaps,
            List<PlanCritiqueFinding> findings,
            PlanCritiqueRubricScores rubric,
            Instant now,
            boolean packetPostedOnPlan,
            java.util.Map<String, Object> workflowState) {

        List<String> reasons = new ArrayList<>();
        int blockingIssues = PlanCritiqueRubric.countUnresolvedBlockingIssues(plan);
        int blockingFindings = countBlockingFindings(findings);
        int gapCount = gaps != null ? gaps.size() : 0;
        boolean openGaps = gapCount > 0;
        boolean blockerGap =
                gaps != null && gaps.stream().anyMatch(g -> "BLOCKER".equalsIgnoreCase(g.getSeverity()));
        int unresolvedQ = countNonBlankUnresolvedQuestions(plan);
        boolean clarificationPending = hasPendingClarification(plan, workflowState);
        boolean repoGroundingMissing = repoGroundingMissing(plan, workflowState);
        boolean structuredMaterialGaps = PlanningPostDraftGovernor.hasStructuredMaterialPlanningGaps(plan);
        int knownFacts = countStructuredKnownFacts(plan);
        List<String> unknownLabels =
                buildMaterialUnknownLabels(
                        gaps, findings, plan, blockingIssues, blockingFindings, clarificationPending, repoGroundingMissing);
        int materialUnknowns =
                computeMaterialUnknownCount(
                        gapCount,
                        unresolvedQ,
                        blockingIssues,
                        blockingFindings,
                        clarificationPending,
                        repoGroundingMissing);

        if (blockerGap) {
            reasons.add("At least one discovery gap is marked BLOCKER.");
        }
        if (!packetPostedOnPlan) {
            reasons.add("Planning packet has not been recorded on the plan state (post step may not have completed).");
        }
        if (blockingIssues > 0) {
            reasons.add(blockingIssues + " BLOCKING issue(s) remain on the plan.");
        }
        if (blockingFindings > 0) {
            reasons.add(blockingFindings + " critique finding(s) block approval.");
        }
        if (openGaps) {
            reasons.add("Open discovery gaps: " + gapCount + ".");
        }
        if (unresolvedQ > 0) {
            reasons.add(unresolvedQ + " unresolved question line(s) on the plan.");
        }
        if (clarificationPending) {
            reasons.add("A clarification is still pending in the coordinator workflow.");
        }
        if (repoGroundingMissing) {
            reasons.add("Repository grounding has not been completed strongly enough for approval.");
        }
        if (structuredMaterialGaps && !clarificationPending) {
            reasons.add("The plan still contains unresolved material questions that should be clarified before approval.");
        }
        reasons.add(
                "Structured planning signals: "
                        + knownFacts
                        + " known fact(s) captured vs "
                        + materialUnknowns
                        + " material unknown(s).");

        double base = rubric != null ? rubric.meanScore() : 0.5;
        base += Math.min(0.18, knownFacts * 0.012);
        base -= Math.min(0.48, materialUnknowns * 0.085);
        base -= PlanCritiqueRubric.governanceAssumptionPenalty(plan);
        base -= Math.min(0.5, blockingIssues * 0.14);
        base -= Math.min(0.45, blockingFindings * 0.12);
        base -= critiqueConfidencePenalty(findings);
        if (clarificationPending) {
            base -= 0.18;
        }
        if (repoGroundingMissing) {
            base -= 0.16;
        }
        if (!packetPostedOnPlan) {
            base -= 0.25;
        }
        double score = Math.max(0.0, Math.min(1.0, base));

        String readiness;
        if (blockerGap) {
            readiness = PlanReadinessStatus.BLOCKED;
        } else if (!packetPostedOnPlan
                || blockingIssues > 0
                || blockingFindings > 0
                || openGaps
                || clarificationPending
                || repoGroundingMissing
                || structuredMaterialGaps) {
            readiness = PlanReadinessStatus.NOT_READY;
        } else if (needsConditional(plan, findings)) {
            readiness = PlanReadinessStatus.CONDITIONALLY_READY;
            reasons.add("Residual assumptions or warnings require explicit human acknowledgment.");
        } else if (score < APPROVAL_CONFIDENCE_THRESHOLD) {
            readiness = PlanReadinessStatus.REVIEWABLE;
            reasons.add("The packet is coherent enough for human review, but confidence is still below approval threshold.");
        } else {
            readiness = PlanReadinessStatus.READY;
        }

        String level;
        if (score >= APPROVAL_CONFIDENCE_THRESHOLD) {
            level = "HIGH";
        } else if (score >= 0.55) {
            level = "MEDIUM";
        } else {
            level = "LOW";
        }

        String notes = summarizeCounts(gaps, findings, plan, knownFacts, materialUnknowns);
        return new PlanConfidence(
                level,
                notes,
                readiness,
                now,
                score,
                List.copyOf(reasons),
                knownFacts,
                materialUnknowns,
                List.copyOf(unknownLabels));
    }

    private static int computeMaterialUnknownCount(
            int gapCount,
            int unresolvedQuestions,
            int blockingIssues,
            int blockingFindings,
            boolean clarificationPending,
            boolean repoGroundingMissing) {
        return Math.max(0, gapCount)
                + Math.max(0, unresolvedQuestions)
                + Math.max(0, blockingIssues)
                + Math.max(0, blockingFindings)
                + (clarificationPending ? 1 : 0)
                + (repoGroundingMissing ? 1 : 0);
    }

    private static int countNonBlankUnresolvedQuestions(FeaturePlanState plan) {
        if (plan == null || plan.getUnresolvedQuestions() == null) {
            return 0;
        }
        int n = 0;
        for (String q : plan.getUnresolvedQuestions()) {
            if (q != null && !q.isBlank()) {
                n++;
            }
        }
        return n;
    }

    private static int countStructuredKnownFacts(FeaturePlanState plan) {
        if (plan == null) {
            return 0;
        }
        int n = plan.getRequirements().size();
        n += plan.getDecisions().size();
        n += plan.getValidationNotes().size();
        n += plan.getRisks().size();
        SolutionOutline so = plan.getSolutionOutline();
        if (so != null && so.getSummary() != null && !so.getSummary().isBlank()) {
            n += 2;
        }
        String pc = plan.getProjectContext() != null ? plan.getProjectContext().getText() : null;
        if (pc != null && pc.trim().length() >= 12) {
            n++;
        }
        String tr = plan.getTraceability() != null ? plan.getTraceability().getText() : null;
        if (tr != null && tr.trim().length() >= 8) {
            n++;
        }
        n += countArtifactValueSignals(plan);
        return n;
    }

    private static int countArtifactValueSignals(FeaturePlanState plan) {
        if (plan.getArtifacts() == null || plan.getArtifacts().isEmpty()) {
            return 0;
        }
        int c = 0;
        for (ArtifactState art : plan.getArtifacts().values()) {
            if (art.getSectionsById() == null) {
                continue;
            }
            for (SectionState sec : art.getSectionsById().values()) {
                if (sec == null) {
                    continue;
                }
                if (!SectionState.STATUS_EMPTY.equalsIgnoreCase(sec.getStatus())) {
                    c++;
                }
                if (sec.getValues() != null) {
                    for (Object v : sec.getValues().values()) {
                        if (v != null && !v.toString().isBlank() && v.toString().trim().length() >= 8) {
                            c++;
                        }
                    }
                }
            }
        }
        return Math.min(36, c);
    }

    private static List<String> buildMaterialUnknownLabels(
            List<DiscoveryGap> gaps,
            List<PlanCritiqueFinding> findings,
            FeaturePlanState plan,
            int blockingIssues,
            int blockingFindings,
            boolean clarificationPending,
            boolean repoGroundingMissing) {
        Set<String> out = new LinkedHashSet<>();
        if (gaps != null) {
            for (DiscoveryGap g : gaps) {
                if (out.size() >= MAX_MATERIAL_UNKNOWN_LABELS) {
                    break;
                }
                String label = firstNonBlank(g.getUserFacingDetail(), g.getGapId());
                if (label != null && !label.isBlank()) {
                    out.add(truncateLabel(label));
                }
            }
        }
        if (plan != null && plan.getUnresolvedQuestions() != null) {
            for (String q : plan.getUnresolvedQuestions()) {
                if (out.size() >= MAX_MATERIAL_UNKNOWN_LABELS) {
                    break;
                }
                if (q != null && !q.isBlank()) {
                    out.add(truncateLabel(q.trim()));
                }
            }
        }
        if (plan != null && blockingIssues > 0) {
            for (var issue : plan.getIssues()) {
                if (out.size() >= MAX_MATERIAL_UNKNOWN_LABELS) {
                    break;
                }
                if (!issue.isBlocking() || !PlanIssueStatus.OPEN.equalsIgnoreCase(issue.getStatus())) {
                    continue;
                }
                String label = firstNonBlank(issue.getTitle(), issue.getDetail());
                if (label != null && !label.isBlank()) {
                    out.add(truncateLabel(label));
                }
            }
        }
        if (findings != null && blockingFindings > 0) {
            for (PlanCritiqueFinding finding : findings) {
                if (out.size() >= MAX_MATERIAL_UNKNOWN_LABELS) {
                    break;
                }
                if (!finding.isBlocksApproval()) {
                    continue;
                }
                String label = firstNonBlank(finding.getMessage(), finding.getCode());
                if (label != null && !label.isBlank()) {
                    out.add(truncateLabel(label));
                }
            }
        }
        if (clarificationPending && out.size() < MAX_MATERIAL_UNKNOWN_LABELS) {
            out.add("Coordinator clarification is still pending.");
        }
        if (repoGroundingMissing && out.size() < MAX_MATERIAL_UNKNOWN_LABELS) {
            out.add("Repository grounding has not been completed for this planning pass.");
        }
        return new ArrayList<>(out);
    }

    private static boolean hasPendingClarification(FeaturePlanState plan, java.util.Map<String, Object> workflowState) {
        if (PlanningReadinessSpread.hasPendingClarification(workflowState)) {
            return true;
        }
        return plan != null && plan.getPlanningIntakeStage() == PlanningIntakeStage.CLARIFYING;
    }

    private static boolean repoGroundingMissing(FeaturePlanState plan, java.util.Map<String, Object> workflowState) {
        if (workflowState == null) {
            return false;
        }
        Object rawObj = workflowState.get("planningRepoEvidenceJson");
        if (rawObj == null) {
            return false;
        }
        String raw = rawObj.toString().trim();
        if (raw.isBlank() || "{}".equals(raw)) {
            return true;
        }
        try {
            JsonNode node = JSON.readTree(raw);
            String workspaceStatus = node.path("workspaceStatus").asText("");
            double score = node.path("repoGroundingScore").asDouble(-1.0);
            boolean localPathPresent = node.path("localPathPresent").asBoolean(false);
            if (workspaceStatus.isBlank() || "unknown".equalsIgnoreCase(workspaceStatus)) {
                return true;
            }
            boolean materialized =
                    workspaceStatus.toUpperCase().contains("MATERIALIZED")
                            || workspaceStatus.toUpperCase().contains("RESOLVED");
            if (!materialized && !localPathPresent) {
                return true;
            }
            if (score >= 0 && score < 0.45) {
                return true;
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return "";
    }

    private static String truncateLabel(String s) {
        String t = s.replace("\r\n", " ").replace("\n", " ").trim();
        return t.length() <= 160 ? t : t.substring(0, 159) + "…";
    }

    private static boolean needsConditional(FeaturePlanState plan, List<PlanCritiqueFinding> findings) {
        if (plan != null) {
            long openHighAssumptions =
                    plan.getAssumptions().stream()
                            .filter(a -> PlanAssumptionStatus.OPEN.equals(a.getStatus()))
                            .filter(a -> PlanGovernanceSeverity.HIGH.equalsIgnoreCase(a.getSeverity()))
                            .count();
            if (openHighAssumptions > 0) {
                return true;
            }
            if (plan.getAssumptions().stream().filter(a -> PlanAssumptionStatus.OPEN.equals(a.getStatus())).count()
                    >= 4) {
                return true;
            }
            if (!plan.getIssues().isEmpty()) {
                return true;
            }
        }
        if (findings != null) {
            long warn = findings.stream().filter(f -> "WARN".equalsIgnoreCase(f.getSeverity())).count();
            if (warn >= 2) {
                return true;
            }
        }
        return false;
    }

    private static int countBlockingFindings(List<PlanCritiqueFinding> findings) {
        if (findings == null) {
            return 0;
        }
        int n = 0;
        for (PlanCritiqueFinding f : findings) {
            if (f.isBlocksApproval()) {
                n++;
            }
        }
        return n;
    }

    private static double critiqueConfidencePenalty(List<PlanCritiqueFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return 0.0;
        }
        double penalty = 0.0;
        for (PlanCritiqueFinding finding : findings) {
            String code = finding.getCode() != null ? finding.getCode().trim().toUpperCase() : "";
            switch (code) {
                case "INSUFFICIENT_REQUEST_EXPLORATION" -> penalty += 0.18;
                case "INSUFFICIENT_OPEN_QUESTIONS", "PLACEHOLDER_PLANNING_FIELD" -> penalty += 0.14;
                case "INSUFFICIENT_CURRENT_STATE", "INSUFFICIENT_ARCHITECTURE_NOTES" -> penalty += 0.12;
                case "FEATURE_SUMMARY_ECHOES_REQUEST" -> penalty += 0.08;
                default -> {
                }
            }
        }
        return Math.min(0.42, penalty);
    }

    private static String summarizeCounts(
            List<DiscoveryGap> gaps,
            List<PlanCritiqueFinding> findings,
            FeaturePlanState plan,
            int knownFacts,
            int materialUnknowns) {
        StringBuilder sb = new StringBuilder();
        sb.append("Known signals ~").append(knownFacts).append(", material unknowns ~").append(materialUnknowns).append(". ");
        if (gaps != null && !gaps.isEmpty()) {
            sb.append("Open gaps: ").append(gaps.size()).append(". ");
        }
        if (findings != null && !findings.isEmpty()) {
            sb.append("Critique findings: ").append(findings.size()).append(". ");
        }
        if (plan != null) {
            if (!plan.getIssues().isEmpty()) {
                sb.append("Issues: ").append(plan.getIssues().size()).append(". ");
            }
            if (!plan.getAssumptions().isEmpty()) {
                sb.append("Assumptions: ").append(plan.getAssumptions().size()).append(". ");
            }
        }
        return sb.toString().trim();
    }
}
