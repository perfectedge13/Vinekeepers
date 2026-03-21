package com.vinekeepers.workflow.planreview;

import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.state.planning.DiscoveryGap;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanCritiqueFinding;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rule-based plan critique (Phase C). Complements {@link com.vinekeepers.workflow.discovery.StructuredDiscoverySupport} gaps.
 */
public final class PlanCritiqueSupport {

    public static final String SOURCE_RULES_V1 = "RULES_V1";

    /** Minimum trimmed length for initial request text before approval is allowed. */
    public static final int MIN_INITIAL_REQUEST_CHARS = 40;
    /** Minimum trimmed length for implementation outline artifact text. */
    public static final int MIN_OUTLINE_CHARS = 120;
    /** Minimum trimmed length for validation approach artifact text. */
    public static final int MIN_VALIDATION_CHARS = 80;
    /** Minimum for architecture design notes (v2). */
    public static final int MIN_ARCHITECTURE_CHARS = 80;
    /** Minimum for risk summary (v2). */
    public static final int MIN_RISK_CHARS = 60;
    /** Minimum for open questions block (v2). */
    public static final int MIN_OPEN_QUESTIONS_CHARS = 40;

    private PlanCritiqueSupport() {}

    public static List<PlanCritiqueFinding> buildFindings(
            FeaturePlanState plan,
            WorkProfileDefinition profile,
            List<DiscoveryGap> gaps) {
        List<PlanCritiqueFinding> out = new ArrayList<>();
        AtomicInteger seq = new AtomicInteger(1);
        if (gaps != null) {
            for (DiscoveryGap g : gaps) {
                out.add(fromGap(g, seq.getAndIncrement()));
            }
        }
        if (plan != null) {
            addSubstanceFindings(plan, out, seq);
            addV2PacketFindings(plan, profile, out, seq);
            if (!plan.getIssues().isEmpty()) {
                out.add(new PlanCritiqueFinding(
                        "crit-iss-" + seq.getAndIncrement(),
                        "RISK",
                        "WARN",
                        "OPEN_ISSUES",
                        plan.getIssues().size() + " open issue(s) recorded on the plan.",
                        ""));
            }
            int ac = plan.getAssumptions().size();
            if (ac > 0) {
                String sev = ac >= 4 ? "WARN" : "INFO";
                out.add(new PlanCritiqueFinding(
                        "crit-asm-" + seq.getAndIncrement(),
                        "RISK",
                        sev,
                        "OPEN_ASSUMPTIONS",
                        ac + " assumption(s) recorded; confirm before implementation.",
                        ""));
            }
            if (plan.getProfileId() == null || plan.getProfileId().isBlank()) {
                out.add(new PlanCritiqueFinding(
                        "crit-prof-" + seq.getAndIncrement(),
                        "PROCESS",
                        "MUST_FIX",
                        "MISSING_PROFILE",
                        "Plan has no profileId.",
                        ""));
            } else if (profile == null) {
                out.add(new PlanCritiqueFinding(
                        "crit-prof-unknown-" + seq.getAndIncrement(),
                        "PROCESS",
                        "MUST_FIX",
                        "UNKNOWN_PROFILE",
                        "Work profile not found for profileId: " + plan.getProfileId(),
                        ""));
            }
        }
        return out;
    }

    /**
     * Blocks approval when the visible planning packet would be mostly placeholders
     * (short/missing request, outline, or validation).
     */
    private static void addSubstanceFindings(FeaturePlanState plan, List<PlanCritiqueFinding> out, AtomicInteger seq) {
        String request = plan.getInitialRequest() != null ? plan.getInitialRequest().trim() : "";
        if (request.length() < MIN_INITIAL_REQUEST_CHARS) {
            out.add(new PlanCritiqueFinding(
                    "crit-sub-req-" + seq.getAndIncrement(),
                    "COVERAGE",
                    "MUST_FIX",
                    "INSUFFICIENT_REQUEST_SUMMARY",
                    "Initial request summary is missing or too short to approve implementation ("
                            + request.length() + " chars; need at least " + MIN_INITIAL_REQUEST_CHARS + ").",
                    "initialRequest"));
        }
        String outline = PlanningArtifactTexts.artifactField(plan, "overall_plan", "outline", "plan_body");
        if (outline.length() < MIN_OUTLINE_CHARS) {
            out.add(new PlanCritiqueFinding(
                    "crit-sub-out-" + seq.getAndIncrement(),
                    "COVERAGE",
                    "MUST_FIX",
                    "INSUFFICIENT_IMPLEMENTATION_OUTLINE",
                    "Implementation outline is missing or too thin ("
                            + outline.length() + " chars; need at least " + MIN_OUTLINE_CHARS + ").",
                    "overall_plan.outline"));
        }
        String validation = PlanningArtifactTexts.artifactField(plan, "validation_plan", "checks", "validation_notes");
        if (validation.length() < MIN_VALIDATION_CHARS) {
            out.add(new PlanCritiqueFinding(
                    "crit-sub-val-" + seq.getAndIncrement(),
                    "COVERAGE",
                    "MUST_FIX",
                    "INSUFFICIENT_VALIDATION_APPROACH",
                    "Validation approach is missing or too thin ("
                            + validation.length() + " chars; need at least " + MIN_VALIDATION_CHARS + ").",
                    "validation_plan.checks"));
        }
    }

    private static void addV2PacketFindings(
            FeaturePlanState plan,
            WorkProfileDefinition profile,
            List<PlanCritiqueFinding> out,
            AtomicInteger seq) {
        if (plan == null || profile == null || profile.findSection("architecture_notes", "impact").isEmpty()) {
            return;
        }
        String arch = PlanningArtifactTexts.artifactField(plan, "architecture_notes", "impact", "architecture_summary");
        if (arch.length() < MIN_ARCHITECTURE_CHARS) {
            out.add(new PlanCritiqueFinding(
                    "crit-v2-arch-" + seq.getAndIncrement(),
                    "COVERAGE",
                    "MUST_FIX",
                    "INSUFFICIENT_ARCHITECTURE_NOTES",
                    "Architecture / design notes are missing or too thin ("
                            + arch.length() + " chars; need at least " + MIN_ARCHITECTURE_CHARS + ").",
                    "architecture_notes.impact"));
        }
        String risks = PlanningArtifactTexts.artifactField(plan, "risk_register", "main", "risk_summary");
        if (risks.length() < MIN_RISK_CHARS) {
            out.add(new PlanCritiqueFinding(
                    "crit-v2-risk-" + seq.getAndIncrement(),
                    "COVERAGE",
                    "MUST_FIX",
                    "INSUFFICIENT_RISK_REGISTER",
                    "Risk / edge-case summary is missing or too thin ("
                            + risks.length() + " chars; need at least " + MIN_RISK_CHARS + ").",
                    "risk_register.main"));
        }
        String oq = PlanningArtifactTexts.artifactField(plan, "open_questions_block", "backlog", "open_questions");
        if (oq.length() < MIN_OPEN_QUESTIONS_CHARS) {
            out.add(new PlanCritiqueFinding(
                    "crit-v2-oq-" + seq.getAndIncrement(),
                    "COVERAGE",
                    "MUST_FIX",
                    "INSUFFICIENT_OPEN_QUESTIONS",
                    "Open questions block is missing or too thin ("
                            + oq.length() + " chars; need at least " + MIN_OPEN_QUESTIONS_CHARS + ").",
                    "open_questions_block.backlog"));
        }
        var art = plan.getArtifacts().get("decision_log");
        var sec = art != null ? art.getSectionsById().get("decisions") : null;
        boolean noDecisions = sec == null || sec.getEntries() == null || sec.getEntries().isEmpty();
        if (noDecisions) {
            out.add(new PlanCritiqueFinding(
                    "crit-v2-dec-" + seq.getAndIncrement(),
                    "PROCESS",
                    "MUST_FIX",
                    "MISSING_DECISION_LOG_ENTRY",
                    "At least one decision log entry is required before approval.",
                    "decision_log.decisions"));
        }
    }

    private static PlanCritiqueFinding fromGap(DiscoveryGap g, int n) {
        String sev = mapGapSeverityToCritique(g.getSeverity());
        String cat = switch (g.getKind() != null ? g.getKind() : "") {
            case "WORKSPACE" -> "WORKSPACE";
            case "REQUIRED_FIELD" -> "COVERAGE";
            default -> "COVERAGE";
        };
        return new PlanCritiqueFinding(
                "crit-gap-" + n,
                cat,
                sev,
                "DISCOVERY_GAP_" + (g.getKind() != null ? g.getKind() : "UNKNOWN"),
                g.getReason() != null ? g.getReason() : "",
                g.getArtifactId() + "." + g.getSectionId() + "." + g.getFieldId());
    }

    private static String mapGapSeverityToCritique(String gapSeverity) {
        if (gapSeverity == null) {
            return "WARN";
        }
        return switch (gapSeverity.trim().toUpperCase()) {
            case "BLOCKER" -> "BLOCKER";
            case "HIGH" -> "MUST_FIX";
            case "MEDIUM" -> "WARN";
            default -> "INFO";
        };
    }
}
