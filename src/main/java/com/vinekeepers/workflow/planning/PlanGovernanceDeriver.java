package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.state.planning.DiscoveryGap;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.PlanDecision;
import com.vinekeepers.state.planning.PlanGovernanceSeverity;
import com.vinekeepers.state.planning.PlanIssue;
import com.vinekeepers.state.planning.PlanIssueStatus;
import com.vinekeepers.state.planning.PlanRisk;
import com.vinekeepers.state.planning.PlanRiskDecisionStatus;
import com.vinekeepers.workflow.discovery.ClarificationPromptQualityGate;
import com.vinekeepers.workflow.discovery.StructuredDiscoverySupport;
import com.vinekeepers.workflow.planreview.PlanningArtifactTexts;
import com.vinekeepers.workflow.planreview.PlanningUserFacingCopy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic merge of discovery gaps, workspace signals, and artifact text into tracked governance records.
 */
public final class PlanGovernanceDeriver {

    private PlanGovernanceDeriver() {}

    public static FeaturePlanState derive(FeaturePlanState plan, WorkProfileDefinition profile) {
        if (plan == null) {
            return null;
        }
        List<DiscoveryGap> gaps = StructuredDiscoverySupport.collectGaps(plan, profile);
        List<PlanIssue> mergedIssues = mergeIssuesFromGaps(plan, gaps);
        List<PlanRisk> mergedRisks = mergeRisks(plan);
        List<PlanDecision> mergedDecisions = mergeDecisions(plan);
        List<String> unresolved = mergeUnresolvedQuestions(plan, profile);
        return plan.withGovernanceRecords(
                plan.getAssumptions(), mergedIssues, mergedRisks, mergedDecisions, unresolved);
    }

    public static void deriveAndPersist(FeaturePlanStateStore store, String contextId, WorkProfileDefinition profile) {
        if (store == null || contextId == null || contextId.isBlank()) {
            return;
        }
        store.getByContextId(contextId)
                .map(p -> derive(p, profile))
                .ifPresent(store::update);
    }

    private static List<PlanIssue> mergeIssuesFromGaps(FeaturePlanState plan, List<DiscoveryGap> gaps) {
        Map<String, PlanIssue> byId = new LinkedHashMap<>();
        for (PlanIssue i : plan.getIssues()) {
            if (i.getId() != null && !i.getId().startsWith("gap:")) {
                byId.put(i.getId(), i);
            }
        }
        if (gaps != null) {
            for (DiscoveryGap g : gaps) {
                if (g == null) {
                    continue;
                }
                String gst = g.getStatus();
                if (gst != null && !gst.isBlank() && !"OPEN".equalsIgnoreCase(gst)) {
                    continue;
                }
                PlanIssue derived = issueFromGap(g);
                byId.put(derived.getId(), derived);
            }
        }
        return List.copyOf(byId.values());
    }

    private static PlanIssue issueFromGap(DiscoveryGap g) {
        String id = "gap:" + g.getGapId();
        String reason = g.getReason() != null ? g.getReason() : "";
        String title = reason.length() > 160 ? reason.substring(0, 159) + "…" : reason;
        if (title.isBlank()) {
            String u = Objects.toString(g.getUserFacingDetail(), "").trim();
            if (!u.isBlank()) {
                title = u.length() > 160 ? u.substring(0, 159) + "…" : u;
            } else {
                String pathLabel =
                        PlanningUserFacingCopy.describePlanningFieldPath(
                                null, g.getArtifactId(), g.getSectionId(), g.getFieldId());
                title =
                        !pathLabel.isBlank()
                                ? "Needs attention: " + pathLabel
                                : "Planning item needs your input";
            }
        }
        String detail = !Objects.toString(g.getUserFacingDetail(), "").isBlank()
                ? g.getUserFacingDetail()
                : reason;
        boolean blocking = isBlockingGap(g);
        String status = blocking ? PlanIssueStatus.BLOCKING : PlanIssueStatus.OPEN;
        String severity = gapToSeverity(g);
        List<String> keys = new ArrayList<>();
        String path = artifactPath(g);
        if (!path.isBlank()) {
            keys.add(path);
        }
        Instant now = Instant.now();
        return new PlanIssue(id, title, detail, status, severity, "DERIVED", "", keys, now, now);
    }

    private static boolean isBlockingGap(DiscoveryGap g) {
        String sev = g.getSeverity() != null ? g.getSeverity().trim().toUpperCase() : "";
        if ("BLOCKER".equals(sev)) {
            return true;
        }
        String kind = g.getKind() != null ? g.getKind() : "";
        if ("WORKSPACE".equals(kind)) {
            return true;
        }
        return "REQUIRED_FIELD".equals(kind) && ("HIGH".equals(sev) || "BLOCKER".equals(sev));
    }

    private static String gapToSeverity(DiscoveryGap g) {
        String sev = g.getSeverity() != null ? g.getSeverity().trim().toUpperCase() : "";
        return switch (sev) {
            case "BLOCKER", "HIGH" -> PlanGovernanceSeverity.HIGH;
            case "MEDIUM" -> PlanGovernanceSeverity.MEDIUM;
            default -> PlanGovernanceSeverity.LOW;
        };
    }

    private static String artifactPath(DiscoveryGap g) {
        String a = g.getArtifactId() != null ? g.getArtifactId() : "";
        String s = g.getSectionId() != null ? g.getSectionId() : "";
        String f = g.getFieldId() != null ? g.getFieldId() : "";
        if (a.isBlank()) {
            return "";
        }
        if (s.isBlank()) {
            return a;
        }
        if (f.isBlank()) {
            return a + "." + s;
        }
        return a + "." + s + "." + f;
    }

    private static List<PlanRisk> mergeRisks(FeaturePlanState plan) {
        Map<String, PlanRisk> byId = new LinkedHashMap<>();
        for (PlanRisk r : plan.getRisks()) {
            if (r.getId() != null && !r.getId().startsWith("art-risk-")) {
                byId.put(r.getId(), r);
            }
        }
        String rs = PlanningArtifactTexts.artifactField(plan, "risk_register", "main", "risk_summary");
        if (!rs.isBlank()) {
            Instant now = Instant.now();
            for (String t : splitArtifactTextEntries(rs, 8)) {
                String id = "art-risk-" + Integer.toHexString(t.hashCode());
                byId.putIfAbsent(
                        id,
                        new PlanRisk(
                                id,
                                t,
                                "",
                                "",
                                PlanRiskDecisionStatus.OPEN,
                                "ARTIFACT",
                                List.of("risk_register.main.risk_summary"),
                                now));
            }
        }
        return List.copyOf(byId.values());
    }

    private static List<PlanDecision> mergeDecisions(FeaturePlanState plan) {
        Map<String, PlanDecision> byId = new LinkedHashMap<>();
        for (PlanDecision d : plan.getDecisions()) {
            if (d.getId() != null && !d.getId().startsWith("art-dec-")) {
                byId.put(d.getId(), d);
            }
        }
        String block = PlanningArtifactTexts.allRepeatableFieldLines(plan, "decision_log", "decisions", "decision_text");
        if (!block.isBlank()) {
            Instant now = Instant.now();
            for (String t : splitArtifactTextEntries(block, 6)) {
                String id = "art-dec-" + Integer.toHexString(t.hashCode());
                byId.putIfAbsent(
                        id,
                        new PlanDecision(
                                id,
                                t,
                                "",
                                PlanRiskDecisionStatus.RESOLVED,
                                "ARTIFACT",
                                List.of("decision_log.decisions.decision_text"),
                                now));
            }
        }
        return List.copyOf(byId.values());
    }

    private static List<String> mergeUnresolvedQuestions(FeaturePlanState plan, WorkProfileDefinition profile) {
        LinkedHashMap<String, Boolean> order = new LinkedHashMap<>();
        for (String q : plan.getUnresolvedQuestions()) {
            if (q == null || q.isBlank()) {
                continue;
            }
            String t = q.trim();
            if (ClarificationPromptQualityGate.isSubstantiveOpenQuestionLine(t)) {
                order.put(t, Boolean.TRUE);
            }
        }
        if (profile != null) {
            for (DiscoveryGap g : StructuredDiscoverySupport.collectGaps(plan, profile)) {
                if (g == null || !"OPEN".equalsIgnoreCase(g.getStatus())) {
                    continue;
                }
                String sev = g.getSeverity() != null ? g.getSeverity().trim().toUpperCase(Locale.ROOT) : "";
                if (!"BLOCKER".equals(sev) && !"HIGH".equals(sev)) {
                    continue;
                }
                String prompt =
                        ClarificationPromptQualityGate.sanitizeBlockingQuestion(g.getUserFacingDetail(), g);
                if (!prompt.isBlank()) {
                    order.putIfAbsent(prompt, Boolean.TRUE);
                }
            }
        }
        return List.copyOf(order.keySet());
    }

    /**
     * Normalizes repeatable-section joins ("1. a\n\n2. b") and bullet lists into one entry per decision/risk line.
     */
    private static List<String> splitArtifactTextEntries(String block, int minLen) {
        List<String> out = new ArrayList<>();
        if (block == null || block.isBlank()) {
            return out;
        }
        String trimmed = block.trim();
        List<String> paragraphs = new ArrayList<>();
        for (String p : trimmed.split("\n\n+")) {
            if (p != null && !p.isBlank()) {
                paragraphs.add(p.trim());
            }
        }
        List<String> units = paragraphs.size() > 1 ? paragraphs : new ArrayList<>();
        if (units.isEmpty()) {
            for (String line : trimmed.split("\\R")) {
                if (line != null && !line.isBlank()) {
                    units.add(line.trim());
                }
            }
        }
        for (String u : units) {
            String t =
                    u.replaceFirst("^\\d+\\.\\s*", "")
                            .replaceFirst("^[-*•]\\s*", "")
                            .trim();
            if (t.length() >= minLen) {
                out.add(t);
            }
        }
        return out;
    }
}
