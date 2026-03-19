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
