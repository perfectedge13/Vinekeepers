package com.vinekeepers.workflow.planreview;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanAssumption;
import com.vinekeepers.state.planning.PlanDecision;
import com.vinekeepers.state.planning.PlanIssue;
import com.vinekeepers.state.planning.PlanIssueStatus;
import com.vinekeepers.state.planning.PlanRisk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the human-readable planning packet for thread review and splits it for Discord size limits.
 */
public final class PlanningThreadPacketFormatter {

    /** Target max chars per Discord message (leave margin below 2000). */
    public static final int DISCORD_CHUNK_TARGET = 1750;

    private static final int SECTION_SOFT_MAX = 1200;

    private PlanningThreadPacketFormatter() {}

    public static String buildFullPacketBody(FeaturePlanState plan, String requestFallback, String repoFallback) {
        if (plan == null) {
            return "";
        }
        String request = firstNonBlank(plan.getInitialRequest(), requestFallback);
        String repo = firstNonBlank(plan.getRepoRef(), repoFallback);

        String exploration = truncate(
                PlanningArtifactTexts.artifactField(plan, "request_exploration", "analysis", "exploration_body"),
                SECTION_SOFT_MAX * 2);
        String featureSummary = truncate(PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "feature_summary"), SECTION_SOFT_MAX);
        String currentState = truncate(
                PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "current_state_summary"),
                SECTION_SOFT_MAX);
        String scope = truncate(PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "scope_summary"), SECTION_SOFT_MAX);
        String stories = truncate(PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "user_stories"), SECTION_SOFT_MAX);
        String acceptance = truncate(PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "acceptance_criteria"), SECTION_SOFT_MAX);
        String planBody = truncate(PlanningArtifactTexts.artifactField(plan, "overall_plan", "outline", "plan_body"), SECTION_SOFT_MAX);
        String validation = truncate(PlanningArtifactTexts.artifactField(plan, "validation_plan", "checks", "validation_notes"), SECTION_SOFT_MAX);
        String context = truncate(PlanningArtifactTexts.artifactField(plan, "project_context", "context", "context_summary"), SECTION_SOFT_MAX);
        String arch = truncate(PlanningArtifactTexts.artifactField(plan, "architecture_notes", "impact", "architecture_summary"), SECTION_SOFT_MAX);
        String comps = truncate(PlanningArtifactTexts.artifactField(plan, "architecture_notes", "impact", "components_impacted"), SECTION_SOFT_MAX);
        String risksArt = truncate(PlanningArtifactTexts.artifactField(plan, "risk_register", "main", "risk_summary"), SECTION_SOFT_MAX);
        String openQ = truncate(PlanningArtifactTexts.artifactField(plan, "open_questions_block", "backlog", "open_questions"), SECTION_SOFT_MAX);
        String decisionsArt = truncate(PlanningArtifactTexts.allRepeatableFieldLines(plan, "decision_log", "decisions", "decision_text"), SECTION_SOFT_MAX * 2);

        StringBuilder sb = new StringBuilder();
        appendSection(sb, "**Request**", request);
        if (repo != null && !repo.isBlank()) {
            appendSection(sb, "**Repo**", repo);
        }
        if (!exploration.isBlank()) {
            appendSection(sb, "**Request exploration**", exploration);
        }
        appendSection(sb, "**Problem / goal**", orPlaceholder(featureSummary));
        appendSection(sb, "**Current state / baseline**", orPlaceholder(currentState));
        appendSection(sb, "**Proposed behavior / outline**", orPlaceholder(planBody));
        appendSection(sb, "**Scope & non-goals**", orPlaceholder(scope));
        if (!stories.isBlank()) {
            appendSection(sb, "**User stories / scenarios**", stories);
        } else {
            appendSection(sb, "**User stories / scenarios**", "_None recorded._");
        }
        appendSection(sb, "**Acceptance criteria**", orPlaceholder(acceptance));
        String table = acceptanceCriteriaTable(acceptance);
        if (!table.isBlank()) {
            appendSection(sb, "**Requirements table (from criteria)**", table);
        }
        if (!comps.isBlank() || !arch.isBlank()) {
            appendSection(
                    sb,
                    "**Architecture & impacted components**",
                    (comps.isBlank() ? "" : "**Components:** " + comps + "\n\n") + (arch.isBlank() ? orPlaceholder("") : arch));
        }
        appendSection(sb, "**Assumptions (tracked)**", formatAssumptionsSection(plan));
        appendSection(sb, "**Issues (tracked)**", formatIssuesSection(plan));
        appendSection(sb, "**Risks (tracked)**", formatRisksSection(plan, risksArt));
        appendSection(
                sb,
                "**Rollout / fallback**",
                risksArt != null && risksArt.length() > 40
                        ? "_See risks above for mitigations; prefer staged enablement and a documented revert path._"
                        : "_Plan staged rollout, monitoring, and a revert path before wide release._");
        appendSection(sb, "**Open questions**", formatOpenQuestionsSection(plan, openQ));
        appendSection(sb, "**Decisions (tracked)**", formatDecisionsSection(plan, decisionsArt));
        appendSection(sb, "**Validation strategy**", orPlaceholder(validation));
        appendSection(sb, "**Project context**", orPlaceholder(context));
        appendSection(sb, "**Readiness snapshot**", formatReadinessSection(plan));
        return sb.toString().trim();
    }

    private static String formatAssumptionsSection(FeaturePlanState plan) {
        if (plan.getAssumptions().isEmpty()) {
            return "_None recorded._";
        }
        return plan.getAssumptions().stream()
                .map(PlanningThreadPacketFormatter::formatAssumptionLine)
                .collect(Collectors.joining("\n"));
    }

    private static String formatAssumptionLine(PlanAssumption a) {
        return "• [" + a.getStatus() + "/" + a.getSeverity() + "] " + a.getStatement().trim();
    }

    private static String formatIssuesSection(FeaturePlanState plan) {
        if (plan.getIssues().isEmpty()) {
            return "_None recorded._";
        }
        List<PlanIssue> sorted = new ArrayList<>(plan.getIssues());
        sorted.sort(
                Comparator.comparing((PlanIssue i) -> !PlanIssueStatus.BLOCKING.equalsIgnoreCase(i.getStatus()))
                        .thenComparing(PlanIssue::getId));
        return sorted.stream().map(PlanningThreadPacketFormatter::formatIssueLine).collect(Collectors.joining("\n"));
    }

    private static String formatIssueLine(PlanIssue i) {
        String head = PlanIssueStatus.BLOCKING.equalsIgnoreCase(i.getStatus()) ? "**BLOCKING** " : "";
        String title = i.getTitle() != null ? i.getTitle().trim() : "";
        String det = i.getDetail() != null ? i.getDetail().trim() : "";
        if (!det.isBlank() && !det.equals(title)) {
            return "• " + head + "[" + i.getStatus() + "/" + i.getSeverity() + "] " + title + " — " + det;
        }
        return "• " + head + "[" + i.getStatus() + "/" + i.getSeverity() + "] " + (title.isBlank() ? i.getText() : title);
    }

    private static String formatRisksSection(FeaturePlanState plan, String artifactFallback) {
        if (!plan.getRisks().isEmpty()) {
            return plan.getRisks().stream()
                    .map(
                            r -> "• [" + r.getStatus() + "] " + r.getStatement().trim()
                                    + (r.getImpact().isBlank() ? "" : " (impact: " + r.getImpact() + ")"))
                    .collect(Collectors.joining("\n"));
        }
        if (artifactFallback != null && !artifactFallback.isBlank()) {
            return artifactFallback;
        }
        return "_None recorded._";
    }

    private static String formatDecisionsSection(FeaturePlanState plan, String artifactFallback) {
        if (!plan.getDecisions().isEmpty()) {
            return plan.getDecisions().stream()
                    .map(d -> "• [" + d.getStatus() + "] " + d.getDecision().trim())
                    .collect(Collectors.joining("\n"));
        }
        if (artifactFallback != null && !artifactFallback.isBlank()) {
            return artifactFallback;
        }
        return "_None recorded._";
    }

    private static String formatOpenQuestionsSection(FeaturePlanState plan, String artifactFallback) {
        if (!plan.getUnresolvedQuestions().isEmpty()) {
            return plan.getUnresolvedQuestions().stream()
                    .map(q -> "• " + q.trim())
                    .collect(Collectors.joining("\n"));
        }
        return orPlaceholder(artifactFallback);
    }

    private static String formatReadinessSection(FeaturePlanState plan) {
        var c = plan.getPlanConfidence();
        var snap = plan.getPlanCritiqueSnapshot();
        if (snap == null) {
            return "_Critique not run yet for this draft — see the pre-approval review message after critique runs._";
        }
        StringBuilder sb = new StringBuilder();
        if (c != null) {
            sb.append("**Readiness:** ")
                    .append(c.getReadinessStatus() != null ? c.getReadinessStatus() : "unknown")
                    .append("\n");
            if (c.getConfidenceScore() >= 0) {
                sb.append("**Confidence score:** ")
                        .append(String.format(java.util.Locale.ROOT, "%.2f", c.getConfidenceScore()))
                        .append("\n");
            }
            if (c.getLevel() != null && !c.getLevel().isBlank()) {
                sb.append("**Level:** ").append(c.getLevel()).append("\n");
            }
            if (c.getConfidenceReasons() != null && !c.getConfidenceReasons().isEmpty()) {
                sb.append("**Reasons:**\n");
                for (String r : c.getConfidenceReasons()) {
                    sb.append("• ").append(r).append("\n");
                }
            }
        }
        if (snap.getRubricScores() != null) {
            var rs = snap.getRubricScores();
            sb.append("**Rubric (0–1):** completeness ")
                    .append(fmt(rs.getCompleteness()))
                    .append(", repo alignment ")
                    .append(fmt(rs.getRepoAlignment()))
                    .append(", approval ")
                    .append(fmt(rs.getApprovalReadiness()));
        }
        String out = sb.toString().trim();
        return out.isBlank() ? "_No readiness summary._" : out;
    }

    private static String fmt(double d) {
        return String.format(java.util.Locale.ROOT, "%.2f", d);
    }

    /**
     * Splits a large packet into Discord-sized chunks, preferring breaks at section boundaries ({@code **Heading**}).
     */
    public static List<String> splitForDiscord(String fullBody) {
        if (fullBody == null || fullBody.isBlank()) {
            return List.of();
        }
        String rest = fullBody.trim();
        if (rest.length() <= DISCORD_CHUNK_TARGET) {
            return List.of("**Planning packet**\n\n" + rest);
        }
        List<String> parts = new ArrayList<>();
        while (!rest.isBlank()) {
            if (rest.length() <= DISCORD_CHUNK_TARGET) {
                parts.add(rest);
                break;
            }
            int cut = findCutPoint(rest, DISCORD_CHUNK_TARGET);
            parts.add(rest.substring(0, cut).trim());
            rest = rest.substring(cut).trim();
        }
        int total = parts.size();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            out.add("**Planning packet (part " + (i + 1) + "/" + total + ")**\n\n" + parts.get(i));
        }
        return out;
    }

    private static int findCutPoint(String rest, int max) {
        int end = Math.min(max, rest.length());
        int best = end;
        int idx = rest.lastIndexOf("\n\n**", end);
        if (idx >= 400) {
            best = idx;
        }
        return Math.min(best, rest.length());
    }

    static String acceptanceCriteriaTable(String acceptance) {
        if (acceptance == null || acceptance.isBlank()) {
            return "";
        }
        List<String> rows = new ArrayList<>();
        for (String line : acceptance.split("\\R")) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            t = t.replaceFirst("^[-*•]\\s*", "").trim();
            if (!t.isEmpty()) {
                rows.add(t);
            }
        }
        if (rows.size() < 2) {
            return "";
        }
        StringBuilder tb = new StringBuilder();
        tb.append("| # | Requirement |\n|---|-------------|\n");
        for (int i = 0; i < rows.size(); i++) {
            String cell = rows.get(i).replace("|", "\\|");
            if (cell.length() > 200) {
                cell = cell.substring(0, 199) + "…";
            }
            tb.append("| ").append(i + 1).append(" | ").append(cell).append(" |\n");
        }
        return tb.toString().trim();
    }

    private static void appendSection(StringBuilder sb, String heading, String content) {
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append(heading).append("\n");
        sb.append(content != null ? content : "");
    }

    private static String orPlaceholder(String s) {
        if (s == null || s.isBlank()) {
            return "_Not drafted yet._";
        }
        return s;
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
