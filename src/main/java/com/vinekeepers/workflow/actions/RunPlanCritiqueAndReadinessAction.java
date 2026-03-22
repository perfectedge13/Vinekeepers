package com.vinekeepers.workflow.actions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.PlanAssumption;
import com.vinekeepers.state.planning.DiscoveryGap;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.PlanIssue;
import com.vinekeepers.state.planning.PlanConfidence;
import com.vinekeepers.state.planning.PlanCritiqueFinding;
import com.vinekeepers.state.planning.PlanCritiqueLifecycleStatus;
import com.vinekeepers.state.planning.PlanCritiqueSnapshot;
import com.vinekeepers.state.planning.PlanReadinessStatus;
import com.vinekeepers.state.planning.PlanningIntakeStage;
import com.vinekeepers.workflow.discovery.StructuredDiscoverySupport;
import com.vinekeepers.workflow.planreview.PlanCritiqueRubric;
import com.vinekeepers.workflow.planreview.PlanCritiqueSupport;
import com.vinekeepers.workflow.planreview.PlanReadinessEvaluator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Phase C: run rule-based critique, compute readiness, persist on {@link FeaturePlanState}, spread keys for workflow.
 * Also merges {@link BuildPlanningThreadReviewBodyAction} output ({@code planningThreadReviewBody},
 * {@code planningThreadReviewBuildError}) for pre-approval thread copy without a separate workflow step.
 */
public final class RunPlanCritiqueAndReadinessAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final ObjectMapper JSON = new ObjectMapper();
    /** Max automatic NEEDS_REVISION → full replan loops per session before forcing human decision. */
    private static final int MAX_PLANNING_AUTO_REVISION_AFTER_CRITIQUE = 2;

    private final FeaturePlanStateStore planStateStore;
    private final WorkProfileRegistry workProfileRegistry;

    public RunPlanCritiqueAndReadinessAction(
            FeaturePlanStateStore planStateStore,
            WorkProfileRegistry workProfileRegistry) {
        this.planStateStore = planStateStore;
        this.workProfileRegistry = workProfileRegistry;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> err = baseErrorSpread("Plan store or work profile registry not available.");
        if (planStateStore == null || workProfileRegistry == null) {
            err.put("planReadinessStatus", com.vinekeepers.state.planning.PlanReadinessStatus.BLOCKED);
            return err;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            Map<String, Object> m = baseErrorSpread("Missing contextId for run_plan_critique_and_readiness.");
            m.put("planReadinessStatus", com.vinekeepers.state.planning.PlanReadinessStatus.BLOCKED);
            return m;
        }
        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            Map<String, Object> m = baseErrorSpread("No FeaturePlanState for contextId: " + contextId);
            m.put("planReadinessStatus", com.vinekeepers.state.planning.PlanReadinessStatus.BLOCKED);
            return m;
        }
        String profileId = plan.getProfileId();
        if (profileId == null || profileId.isBlank()) {
            Map<String, Object> m = baseErrorSpread("FeaturePlanState has no profileId.");
            m.put("planReadinessStatus", com.vinekeepers.state.planning.PlanReadinessStatus.BLOCKED);
            return m;
        }
        WorkProfileDefinition profile = workProfileRegistry.get(profileId).orElse(null);

        try {
            List<DiscoveryGap> gaps = StructuredDiscoverySupport.collectGaps(plan, profile);
            List<PlanCritiqueFinding> findings = new ArrayList<>(PlanCritiqueSupport.buildFindings(plan, profile, gaps));
            boolean discoveryDone =
                    "true".equalsIgnoreCase(String.valueOf(state != null ? state.get("humanDiscoveryCompleted") : null));
            if (plan.getPlanningIntakeStage() == PlanningIntakeStage.GATHERING_CONTEXT && !discoveryDone) {
                findings.add(0, new PlanCritiqueFinding(
                        "crit-intake-disc-1",
                        "PROCESS",
                        "MUST_FIX",
                        "INTAKE_DISCOVERY_INCOMPLETE",
                        "Complete the coordinator discovery kickoff in this thread (reply to the first planning prompt) before the plan can advance to approval.",
                        ""));
            }
            Instant now = Instant.now();
            int blockingFc =
                    (int) findings.stream().filter(PlanCritiqueFinding::isBlocksApproval).count();
            var rubric = PlanCritiqueRubric.compute(plan, findings, blockingFc);
            List<String> revisions =
                    findings.stream()
                            .filter(PlanCritiqueFinding::isBlocksApproval)
                            .map(PlanCritiqueFinding::getId)
                            .toList();
            PlanCritiqueSnapshot snapshot =
                    new PlanCritiqueSnapshot(
                            now,
                            PlanCritiqueSupport.SOURCE_RULES_V1,
                            findings,
                            PlanCritiqueLifecycleStatus.COMPLETE,
                            rubric,
                            blockingFc,
                            revisions);
            PlanConfidence confidence = PlanReadinessEvaluator.evaluate(plan, gaps, findings, now, state);
            if ("true".equalsIgnoreCase(String.valueOf(bind != null ? bind.get("humanReadinessProceedAck") : null))) {
                confidence =
                        new PlanConfidence(
                                confidence.getLevel(),
                                "Human acknowledged warnings; proceeding to approval. "
                                        + (confidence.getNotes() != null ? confidence.getNotes() : ""),
                                PlanReadinessStatus.READY,
                                now,
                                confidence.getConfidenceScore(),
                                confidence.getConfidenceReasons());
            }
            String legacyStatus = PlanReadinessStatus.legacySpreadValue(confidence.getReadinessStatus());
            String summaryForSpread = confidence.getNotes() != null ? confidence.getNotes() : "";
            String prevCtr = getString(state, "planningCritiqueAutoRevisionCount");
            int prevRevision = parseNonNegativeInt(prevCtr, 0);
            PlanConfidence confidenceForStore = confidence;
            boolean critiqueAutoReplansCapped = false;
            if (PlanReadinessStatus.NEEDS_REVISION.equals(legacyStatus)
                    && prevRevision >= MAX_PLANNING_AUTO_REVISION_AFTER_CRITIQUE) {
                critiqueAutoReplansCapped = true;
                legacyStatus = PlanReadinessStatus.NEEDS_HUMAN_DECISION;
                summaryForSpread =
                        summaryForSpread
                                + "\n\n(Automatic full replanning after critique is limited. Use the coordinator menu or **Revise plan first** to continue.)";
                confidenceForStore =
                        new PlanConfidence(
                                confidence.getLevel(),
                                summaryForSpread,
                                PlanReadinessStatus.NEEDS_HUMAN_DECISION,
                                now,
                                confidence.getConfidenceScore(),
                                confidence.getConfidenceReasons());
            }
            FeaturePlanState next = plan.withPlanCritiqueSnapshot(snapshot).withPlanConfidence(confidenceForStore);
            planStateStore.update(next);

            Map<String, Object> spread = new LinkedHashMap<>();
            spread.put("planCritiqueError", "");
            if (PlanReadinessStatus.READY.equals(legacyStatus)) {
                spread.put("planningCritiqueAutoRevisionCount", "0");
            } else if (critiqueAutoReplansCapped) {
                spread.put("planningCritiqueAutoRevisionCount", "0");
            } else if (PlanReadinessStatus.NEEDS_REVISION.equals(legacyStatus)) {
                spread.put("planningCritiqueAutoRevisionCount", String.valueOf(prevRevision + 1));
            } else if (prevCtr != null && !prevCtr.isBlank()) {
                spread.put("planningCritiqueAutoRevisionCount", prevCtr);
            }
            spread.put("planReadinessStatus", legacyStatus);
            spread.put("planConfidenceLevel", confidenceForStore.getLevel() != null ? confidenceForStore.getLevel() : "");
            spread.put(
                    "planConfidenceScore",
                    confidenceForStore.getConfidenceScore() >= 0
                            ? String.format(java.util.Locale.ROOT, "%.3f", confidenceForStore.getConfidenceScore())
                            : "");
            spread.put("planReadinessSummary", summaryForSpread);
            spread.put("planCritiqueFindingsJson", JSON.writeValueAsString(findings));
            spread.put("planCritiqueSummary", formatCritiqueSummary(findings));
            putAssumptionIssueSummaries(spread, next);
            mergePlanningThreadReview(spread, event, state, bind);
            return spread;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "critique failed";
            Map<String, Object> m = baseErrorSpread(msg);
            m.put("planReadinessStatus", com.vinekeepers.state.planning.PlanReadinessStatus.BLOCKED);
            try {
                m.put("planCritiqueFindingsJson", JSON.writeValueAsString(List.of()));
            } catch (JsonProcessingException ignored) {
                m.put("planCritiqueFindingsJson", "[]");
            }
            putAssumptionIssueSummaries(m, plan);
            mergePlanningThreadReview(m, event, state, bind);
            return m;
        }
    }

    private static void putAssumptionIssueSummaries(Map<String, Object> spread, FeaturePlanState plan) {
        if (plan == null) {
            spread.put("planAssumptionsSummary", "");
            spread.put("planIssuesSummary", "");
            return;
        }
        spread.put("planAssumptionsSummary", formatEntryBullets(plan.getAssumptions()));
        spread.put("planIssuesSummary", formatEntryBullets(plan.getIssues()));
    }

    private static String formatEntryBullets(List<?> entries) {
        if (entries == null || entries.isEmpty()) {
            return "_None recorded._";
        }
        String joined = entries.stream()
                .map(e -> {
                    if (e instanceof PlanAssumption a) {
                        return a.getStatement();
                    }
                    if (e instanceof PlanIssue i) {
                        return i.getText();
                    }
                    return e != null ? e.toString() : "";
                })
                .filter(s -> s != null && !s.isBlank())
                .map(s -> "• " + s.trim())
                .collect(Collectors.joining("\n"));
        return joined.isBlank() ? "_None recorded._" : joined;
    }

    private void mergePlanningThreadReview(Map<String, Object> spread, Event event, Map<String, Object> state, Map<String, Object> bind) {
        if (planStateStore == null) {
            spread.putIfAbsent("planningThreadReviewBody", "");
            spread.putIfAbsent("planningThreadReviewBuildError", "");
            return;
        }
        Object review = new BuildPlanningThreadReviewBodyAction(planStateStore).run(event, state, bind);
        if (review instanceof Map<?, ?> raw) {
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                if (e.getKey() != null) {
                    spread.put(e.getKey().toString(), e.getValue());
                }
            }
        }
    }

    private static Map<String, Object> baseErrorSpread(String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planCritiqueError", error);
        m.put("planReadinessStatus", "");
        m.put("planConfidenceLevel", "");
        m.put("planReadinessSummary", "");
        m.put("planCritiqueFindingsJson", "[]");
        m.put("planCritiqueSummary", "");
        m.put("planningThreadReviewBody", "");
        m.put("planningThreadReviewBuildError", "");
        m.put("planAssumptionsSummary", "");
        m.put("planIssuesSummary", "");
        return m;
    }

    private static String formatCritiqueSummary(List<PlanCritiqueFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return "No critique findings.";
        }
        return findings.stream()
                .map(f -> "• [" + f.getSeverity() + "] " + f.getMessage())
                .collect(Collectors.joining("\n"));
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }

    private static int parseNonNegativeInt(String raw, int dflt) {
        if (raw == null || raw.isBlank()) {
            return dflt;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return v >= 0 ? v : dflt;
        } catch (NumberFormatException e) {
            return dflt;
        }
    }
}
