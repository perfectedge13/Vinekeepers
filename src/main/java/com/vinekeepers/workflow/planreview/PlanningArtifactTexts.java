package com.vinekeepers.workflow.planreview;

import com.vinekeepers.profile.ArtifactState;
import com.vinekeepers.profile.SectionState;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanIssue;
import com.vinekeepers.state.planning.PlanIssueStatus;
import com.vinekeepers.state.planning.PlanRisk;
import com.vinekeepers.state.planning.PlanRiskDecisionStatus;

import com.vinekeepers.workflow.discovery.ClarificationPromptQualityGate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads canonical artifact section values (same paths as proposal auto-apply / thread review body).
 */
public final class PlanningArtifactTexts {

    private PlanningArtifactTexts() {}

    /** All rows of a repeatable section joined (e.g. full decision log). */
    public static String allRepeatableFieldLines(FeaturePlanState plan, String artifactId, String sectionId, String fieldId) {
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
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (Map<String, Object> row : entries) {
            Object v = row.get(fieldId);
            if (v == null) {
                continue;
            }
            String t = v.toString().trim();
            if (t.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(i++).append(". ").append(t);
        }
        return sb.toString();
    }

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

    /**
     * Unresolved-question lines safe to show in the planning packet (drops meta/completeness filler and internal errors).
     */
    public static List<String> substantiveUnresolvedQuestionLines(FeaturePlanState plan) {
        if (plan == null) {
            return List.of();
        }
        LinkedHashSet<String> order = new LinkedHashSet<>();
        for (String q : plan.getUnresolvedQuestions()) {
            if (q == null || q.isBlank()) {
                continue;
            }
            String t = q.trim();
            if (ClarificationPromptQualityGate.isSubstantiveOpenQuestionLine(t)) {
                order.add(t);
            }
        }
        for (PlanIssue i : plan.getIssues()) {
            if (i == null) {
                continue;
            }
            String st = i.getStatus();
            if (!PlanIssueStatus.OPEN.equals(st) && !PlanIssueStatus.BLOCKING.equals(st)) {
                continue;
            }
            String line = firstNonBlankIssueLine(i);
            if (!line.isBlank() && ClarificationPromptQualityGate.isSubstantiveOpenQuestionLine(line)) {
                order.add(line);
            }
        }
        for (PlanRisk r : plan.getRisks()) {
            if (r == null || !PlanRiskDecisionStatus.OPEN.equals(r.getStatus())) {
                continue;
            }
            String line = r.getStatement() != null ? r.getStatement().trim() : "";
            if (!line.isBlank() && ClarificationPromptQualityGate.isSubstantiveOpenQuestionLine(line)) {
                order.add(line);
            }
        }
        return List.copyOf(order);
    }

    private static String firstNonBlankIssueLine(PlanIssue i) {
        String t = i.getTitle() != null ? i.getTitle().trim() : "";
        if (!t.isBlank()) {
            return t;
        }
        return i.getDetail() != null ? i.getDetail().trim() : "";
    }

    public static String unresolvedQuestionSummary(FeaturePlanState plan) {
        if (plan == null) {
            return "";
        }
        List<String> lines = substantiveUnresolvedQuestionLines(plan);
        return lines.stream().collect(Collectors.joining("\n"));
    }
}
