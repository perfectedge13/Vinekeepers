package com.vinekeepers.workflow.planreview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanAssumption;
import com.vinekeepers.state.planning.PlanDecision;
import com.vinekeepers.state.planning.PlanIssue;
import com.vinekeepers.state.planning.PlanIssueStatus;
import com.vinekeepers.state.planning.PlanRisk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * Builds the human-readable planning packet for thread review and splits it for Discord size limits.
 */
public final class PlanningThreadPacketFormatter {

    /** Target max chars per Discord message (leave margin below 2000). */
    public static final int DISCORD_CHUNK_TARGET = 1750;

    private static final int SECTION_SOFT_MAX = 1200;

    private static final int ITEM_TEXT_MAX = 320;

    private static final String TRUNCATION_FOOTNOTE = "\n\n_(Truncated for display length.)_";

    private static final Pattern NUMBERED_ITEM_BOUNDARY = Pattern.compile("\\n\\n\\d+\\.\\s");

    private static final ObjectMapper REPO_EVIDENCE_JSON = new ObjectMapper();

    private PlanningThreadPacketFormatter() {}

    public static String buildFullPacketBody(FeaturePlanState plan, String requestFallback, String repoFallback) {
        return buildFullPacketBody(plan, requestFallback, repoFallback, null);
    }

    /**
     * @param repoEvidenceJson optional {@code planningRepoEvidenceJson} from workflow state (may be blank).
     */
    public static String buildFullPacketBody(
            FeaturePlanState plan, String requestFallback, String repoFallback, String repoEvidenceJson) {
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
        String storiesRaw = PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "user_stories");
        String stories = truncate(storiesRaw, SECTION_SOFT_MAX);
        String acceptance = truncate(PlanningArtifactTexts.artifactField(plan, "requirements_spec", "narrative", "acceptance_criteria"), SECTION_SOFT_MAX);
        String planBody = truncate(PlanningArtifactTexts.artifactField(plan, "overall_plan", "outline", "plan_body"), SECTION_SOFT_MAX);
        String validation = truncate(PlanningArtifactTexts.artifactField(plan, "validation_plan", "checks", "validation_notes"), SECTION_SOFT_MAX);
        String context = truncate(PlanningArtifactTexts.artifactField(plan, "project_context", "context", "context_summary"), SECTION_SOFT_MAX);
        String arch = truncate(PlanningArtifactTexts.artifactField(plan, "architecture_notes", "impact", "architecture_summary"), SECTION_SOFT_MAX);
        String comps = truncate(PlanningArtifactTexts.artifactField(plan, "architecture_notes", "impact", "components_impacted"), SECTION_SOFT_MAX);
        String risksArt = truncate(PlanningArtifactTexts.artifactField(plan, "risk_register", "main", "risk_summary"), SECTION_SOFT_MAX);
        String openQ = truncate(PlanningArtifactTexts.effectiveOpenQuestions(plan), SECTION_SOFT_MAX);
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
            String storiesBlock = stories;
            String storiesList = bulletLinesAsDiscordList("Story / scenario", storiesRaw);
            if (!storiesList.isBlank()) {
                storiesBlock = storiesBlock + "\n\n" + storiesList;
            }
            appendSection(sb, "**User stories / scenarios**", storiesBlock);
        } else {
            appendSection(sb, "**User stories / scenarios**", "_None recorded._");
        }
        appendSection(sb, "**Acceptance criteria**", orPlaceholder(acceptance));
        String criteriaList = bulletLinesAsDiscordList("Requirement", acceptance);
        if (!criteriaList.isBlank()) {
            appendSection(sb, "**Requirements list (from criteria)**", criteriaList);
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
        String core = sb.toString().trim();
        String grounding = summarizeRepoEvidenceForHumans(repoEvidenceJson);
        if (grounding.isBlank()) {
            return core;
        }
        StringBuilder out = new StringBuilder(core);
        appendSection(out, "**Repo / workspace (grounding)**", grounding);
        return out.toString().trim();
    }

    /**
     * Short follow-up copy when the full packet already lives in-thread: avoids repeating the same draft body after
     * {@link com.vinekeepers.workflow.actions.PostPlanningPacketThreadAction}.
     */
    public static String buildConciseThreadReviewBodyAfterPacket(FeaturePlanState plan, String requestFallback) {
        if (plan == null) {
            return "";
        }
        String request = firstNonBlank(plan.getInitialRequest(), requestFallback);
        String gist =
                request != null && request.length() > 200 ? request.substring(0, 199) + "…" : (request != null ? request : "");
        StringBuilder sb = new StringBuilder();
        sb.append(
                "**Planning packet** — The messages above labeled **Planning packet** are the full draft; treat them as the source of truth.\n\n");
        if (!gist.isBlank()) {
            sb.append("**Request focus:** ").append(gist.trim()).append("\n\n");
        }
        sb.append(
                "**This review message** only adds readiness, assumptions/issues, and critique bullets — not a second copy of the packet.");
        return sb.toString().trim();
    }

    /**
     * Compact markdown-friendly summary of {@code planningRepoEvidenceJson} for packets, critique copy, and orchestrator text.
     */
    public static String summarizeRepoEvidenceForHumans(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        String t = json.trim();
        if ("{}".equals(t)) {
            return "";
        }
        try {
            JsonNode n = REPO_EVIDENCE_JSON.readTree(t);
            StringBuilder b = new StringBuilder();
            lineIf(b, "Plan context", textOrEmpty(n, "contextId"));
            lineIf(b, "Feature", textOrEmpty(n, "featureSlug"));
            lineIf(b, "Repo", textOrEmpty(n, "repoRef"));
            lineIf(b, "Workspace id", textOrEmpty(n, "workspaceId"));
            lineIf(b, "Workspace status", textOrEmpty(n, "workspaceStatus"));
            if (n.path("localPathPresent").asBoolean(false)) {
                b.append("- Local clone path is recorded for exploration.\n");
            } else {
                b.append("- No local clone path yet — drafting may rely on request text only.\n");
            }
            String stage = textOrEmpty(n, "planningIntakeStage");
            if (!stage.isBlank()) {
                b.append("- Intake stage: **").append(stage).append("**.\n");
            }
            if (n.path("blockingIssues").isIntegralNumber() && n.path("blockingIssues").asInt() > 0) {
                b.append("- Blocking issues on plan: **").append(n.path("blockingIssues").asInt()).append("**.\n");
            }
            String notes = textOrEmpty(n, "accessNotes");
            if (!notes.isBlank()) {
                b.append("- Access notes: ")
                        .append(notes.length() > 220 ? notes.substring(0, 217) + "…" : notes)
                        .append('\n');
            }
            return b.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static void lineIf(StringBuilder b, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        b.append("- ").append(label).append(": `").append(value).append("`.\n");
    }

    private static String textOrEmpty(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return "";
        }
        return n.get(field).asText("").trim();
    }

    private static String formatAssumptionsSection(FeaturePlanState plan) {
        if (plan.getAssumptions().isEmpty()) {
            return "_None recorded._";
        }
        StringBuilder tb = new StringBuilder();
        int i = 1;
        for (PlanAssumption a : plan.getAssumptions()) {
            appendItemSeparator(tb);
            tb.append(i++)
                    .append(". Status: ")
                    .append(normalizeInlineText(PlanningUserFacingCopy.humanizeAssumptionStatus(a.getStatus()), 80))
                    .append(" | Severity: ")
                    .append(normalizeInlineText(PlanningUserFacingCopy.humanizeGovernanceSeverity(a.getSeverity()), 80))
                    .append('\n');
            appendIndentedField(tb, "Assumption", a.getStatement(), ITEM_TEXT_MAX);
        }
        return tb.toString().trim();
    }

    private static String formatIssuesSection(FeaturePlanState plan) {
        if (plan.getIssues().isEmpty()) {
            return "_None recorded._";
        }
        List<PlanIssue> sorted = new ArrayList<>(plan.getIssues());
        sorted.sort(
                Comparator.comparing((PlanIssue i) -> !PlanIssueStatus.BLOCKING.equalsIgnoreCase(i.getStatus()))
                        .thenComparing(PlanIssue::getId));
        StringBuilder tb = new StringBuilder();
        int idx = 1;
        for (PlanIssue i : sorted) {
            String blocking =
                    PlanIssueStatus.BLOCKING.equalsIgnoreCase(i.getStatus()) ? "Blocking" : "—";
            String st = normalizeInlineText(PlanningUserFacingCopy.humanizeIssueStatus(i.getStatus()), 80);
            String sev = normalizeInlineText(PlanningUserFacingCopy.humanizeGovernanceSeverity(i.getSeverity()), 80);
            String title = i.getTitle() != null ? i.getTitle().trim() : "";
            String det = i.getDetail() != null ? i.getDetail().trim() : "";
            String topic;
            if (!det.isBlank() && !det.equals(title)) {
                topic = title + " — " + det;
            } else {
                topic = !title.isBlank() ? title : (!det.isBlank() ? det : "Issue details pending.");
            }
            appendItemSeparator(tb);
            tb.append(idx++)
                    .append(". Priority: ")
                    .append(normalizeInlineText(blocking, 40))
                    .append(" | Status: ")
                    .append(st)
                    .append(" | Severity: ")
                    .append(sev)
                    .append('\n');
            appendIndentedField(tb, "Topic", topic, ITEM_TEXT_MAX);
        }
        return tb.toString().trim();
    }

    private static String formatRisksSection(FeaturePlanState plan, String artifactFallback) {
        if (!plan.getRisks().isEmpty()) {
            StringBuilder tb = new StringBuilder();
            int idx = 1;
            for (PlanRisk r : plan.getRisks()) {
                appendItemSeparator(tb);
                tb.append(idx++)
                        .append(". Status: ")
                        .append(normalizeInlineText(PlanningUserFacingCopy.humanizeRiskDecisionStatus(r.getStatus()), 80))
                        .append(" | Impact: ")
                        .append(normalizeInlineText(r.getImpact(), 120))
                        .append(" | Likelihood: ")
                        .append(normalizeInlineText(r.getLikelihood(), 80))
                        .append('\n');
                appendIndentedField(tb, "Risk", r.getStatement(), ITEM_TEXT_MAX);
            }
            return tb.toString().trim();
        }
        if (artifactFallback != null && !artifactFallback.isBlank()) {
            String list = bulletLinesAsDiscordList("Risk", artifactFallback);
            if (!list.isBlank()) {
                return list;
            }
            return artifactFallback;
        }
        return "_None recorded._";
    }

    private static String formatDecisionsSection(FeaturePlanState plan, String artifactFallback) {
        if (!plan.getDecisions().isEmpty()) {
            StringBuilder tb = new StringBuilder();
            int idx = 1;
            for (PlanDecision d : plan.getDecisions()) {
                appendItemSeparator(tb);
                tb.append(idx++)
                        .append(". Status: ")
                        .append(normalizeInlineText(PlanningUserFacingCopy.humanizeRiskDecisionStatus(d.getStatus()), 80))
                        .append('\n');
                appendIndentedField(tb, "Decision", d.getDecision(), ITEM_TEXT_MAX);
                tb.append('\n');
                appendIndentedField(tb, "Rationale", d.getRationale(), ITEM_TEXT_MAX);
            }
            return tb.toString().trim();
        }
        if (artifactFallback != null && !artifactFallback.isBlank()) {
            String list = bulletLinesAsDiscordList("Decision", artifactFallback);
            if (!list.isBlank()) {
                return list;
            }
            return artifactFallback;
        }
        return "_None recorded._";
    }

    private static String formatOpenQuestionsSection(FeaturePlanState plan, String artifactFallback) {
        List<String> rows = PlanningArtifactTexts.substantiveUnresolvedQuestionLines(plan);
        if (!rows.isEmpty()) {
            StringBuilder tb = new StringBuilder();
            int i = 1;
            for (String q : rows) {
                appendItemSeparator(tb);
                tb.append(i++)
                        .append(". Open question: ")
                        .append(normalizeInlineText(q, ITEM_TEXT_MAX));
            }
            return tb.toString().trim();
        }
        if (artifactFallback != null && !artifactFallback.isBlank()) {
            String list = bulletLinesAsDiscordList("Open question", artifactFallback);
            if (!list.isBlank()) {
                return list;
            }
            return orPlaceholder(artifactFallback);
        }
        return orPlaceholder(artifactFallback);
    }

    private static String formatReadinessSection(FeaturePlanState plan) {
        var c = plan.getPlanConfidence();
        var snap = plan.getPlanCritiqueSnapshot();
        if (snap == null) {
            return "_Readiness has not been computed for this draft yet — it will appear after the next review pass._";
        }
        StringBuilder sb = new StringBuilder();
        if (c != null) {
            sb.append("**Readiness:** ")
                    .append(
                            PlanningUserFacingCopy.humanizeReadinessStatus(
                                    c.getReadinessStatus() != null ? c.getReadinessStatus() : "unknown"))
                    .append("\n");
            if (c.getConfidenceScore() >= 0) {
                sb.append("**Confidence score:** ")
                        .append(String.format(java.util.Locale.ROOT, "%.2f", c.getConfidenceScore()))
                        .append("\n");
            }
            if (c.getLevel() != null && !c.getLevel().isBlank()) {
                sb.append("**Confidence level:** ")
                        .append(PlanningUserFacingCopy.humanizeGovernanceSeverity(c.getLevel()))
                        .append("\n");
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
            sb.append("**Quality checks (0–1 scale):** completeness ")
                    .append(fmt(rs.getCompleteness()))
                    .append(", repo alignment ")
                    .append(fmt(rs.getRepoAlignment()))
                    .append(", approval readiness ")
                    .append(fmt(rs.getApprovalReadiness()));
        }
        String out = sb.toString().trim();
        return out.isBlank() ? "_No readiness summary._" : out;
    }

    private static String fmt(double d) {
        return String.format(java.util.Locale.ROOT, "%.2f", d);
    }

    /**
     * Splits a large packet into Discord-sized chunks, preferring breaks at section or numbered-item boundaries.
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
        int sectionIdx = rest.lastIndexOf("\n\n**", end);
        int itemIdx = findLastMatchStart(NUMBERED_ITEM_BOUNDARY, rest, end);
        if (sectionIdx >= 400 || itemIdx >= 400) {
            best = Math.max(sectionIdx, itemIdx);
        }
        return Math.min(best > 0 ? best : end, rest.length());
    }

    /**
     * Renders bullet- or numbered lines as a Discord-friendly numbered list when there are at least two rows.
     * Shared by planning packets and role thread summaries.
     */
    public static String bulletLinesAsDiscordList(String itemColumnTitle, String multilineText) {
        if (multilineText == null || multilineText.isBlank()) {
            return "";
        }
        List<String> rows = new ArrayList<>();
        for (String line : multilineText.split("\\R")) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            t = t.replaceFirst("^\\d+\\.\\s*", "").replaceFirst("^[-*•]\\s*", "").trim();
            if (!t.isEmpty()) {
                rows.add(t);
            }
        }
        if (rows.size() < 2) {
            return "";
        }
        StringBuilder tb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            appendItemSeparator(tb);
            tb.append(i + 1).append(". ");
            if (itemColumnTitle != null && !itemColumnTitle.isBlank()) {
                tb.append(normalizeInlineText(itemColumnTitle, 80)).append(": ");
            }
            tb.append(normalizeInlineText(rows.get(i), ITEM_TEXT_MAX));
        }
        return tb.toString().trim();
    }

    public static String bulletLinesAsMarkdownTable(String itemColumnTitle, String multilineText) {
        return bulletLinesAsDiscordList(itemColumnTitle, multilineText);
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
        return s.substring(0, max - 1) + "…" + TRUNCATION_FOOTNOTE;
    }

    private static void appendItemSeparator(StringBuilder sb) {
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
    }

    private static void appendIndentedField(StringBuilder sb, String label, String value, int maxLen) {
        sb.append("   ")
                .append(label)
                .append(": ")
                .append(normalizeInlineText(value, maxLen));
    }

    private static int findLastMatchStart(Pattern pattern, String text, int maxInclusive) {
        Matcher matcher = pattern.matcher(text);
        int best = -1;
        while (matcher.find()) {
            if (matcher.start() > maxInclusive) {
                break;
            }
            best = matcher.start();
        }
        return best;
    }

    private static String normalizeInlineText(String s, int maxLen) {
        if (s == null || s.isBlank()) {
            return "—";
        }
        String t = s.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').replaceAll("\\s+", " ").trim();
        if (t.length() > maxLen) {
            t = t.substring(0, Math.max(0, maxLen - 1)) + "…";
        }
        return t.isBlank() ? "—" : t;
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
