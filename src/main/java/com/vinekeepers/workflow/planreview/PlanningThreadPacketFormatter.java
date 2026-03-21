package com.vinekeepers.workflow.planreview;

import com.vinekeepers.state.planning.FeaturePlanState;

import java.util.ArrayList;
import java.util.List;
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

        String featureSummary = truncate(PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "feature_summary"), SECTION_SOFT_MAX);
        String scope = truncate(PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "scope_summary"), SECTION_SOFT_MAX);
        String stories = truncate(PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "user_stories"), SECTION_SOFT_MAX);
        String acceptance = truncate(PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "acceptance_criteria"), SECTION_SOFT_MAX);
        String planBody = truncate(PlanningArtifactTexts.artifactField(plan, "overall_plan", "outline", "plan_body"), SECTION_SOFT_MAX);
        String validation = truncate(PlanningArtifactTexts.artifactField(plan, "validation_plan", "checks", "validation_notes"), SECTION_SOFT_MAX);
        String context = truncate(PlanningArtifactTexts.artifactField(plan, "project_context", "context", "context_summary"), SECTION_SOFT_MAX);
        String arch = truncate(PlanningArtifactTexts.artifactField(plan, "architecture_notes", "impact", "architecture_summary"), SECTION_SOFT_MAX);
        String comps = truncate(PlanningArtifactTexts.artifactField(plan, "architecture_notes", "impact", "components_impacted"), SECTION_SOFT_MAX);
        String risks = truncate(PlanningArtifactTexts.artifactField(plan, "risk_register", "main", "risk_summary"), SECTION_SOFT_MAX);
        String openQ = truncate(PlanningArtifactTexts.artifactField(plan, "open_questions_block", "backlog", "open_questions"), SECTION_SOFT_MAX);
        String decisions = truncate(PlanningArtifactTexts.allRepeatableFieldLines(plan, "decision_log", "decisions", "decision_text"), SECTION_SOFT_MAX * 2);

        StringBuilder sb = new StringBuilder();
        appendSection(sb, "**Request**", request);
        if (repo != null && !repo.isBlank()) {
            appendSection(sb, "**Repo**", repo);
        }
        appendSection(sb, "**Problem / goal**", orPlaceholder(featureSummary));
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
        appendSection(sb, "**Risks, edge cases, rollout**", orPlaceholder(risks));
        appendSection(sb, "**Open questions**", orPlaceholder(openQ));
        if (!decisions.isBlank()) {
            appendSection(sb, "**Decision log**", decisions);
        }
        appendSection(sb, "**Validation strategy**", orPlaceholder(validation));
        appendSection(sb, "**Project context**", orPlaceholder(context));
        return sb.toString().trim();
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
