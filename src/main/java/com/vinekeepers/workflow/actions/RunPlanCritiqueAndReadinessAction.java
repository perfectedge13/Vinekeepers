package com.vinekeepers.workflow.actions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.DiscoveryGap;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.PlanConfidence;
import com.vinekeepers.state.planning.PlanCritiqueFinding;
import com.vinekeepers.state.planning.PlanCritiqueSnapshot;
import com.vinekeepers.workflow.discovery.StructuredDiscoverySupport;
import com.vinekeepers.workflow.planreview.PlanCritiqueSupport;
import com.vinekeepers.workflow.planreview.PlanReadinessEvaluator;

import java.time.Instant;
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
            List<PlanCritiqueFinding> findings = PlanCritiqueSupport.buildFindings(plan, profile, gaps);
            Instant now = Instant.now();
            PlanCritiqueSnapshot snapshot = new PlanCritiqueSnapshot(now, PlanCritiqueSupport.SOURCE_RULES_V1, findings);
            PlanConfidence confidence = PlanReadinessEvaluator.evaluate(plan, gaps, findings, now);
            FeaturePlanState next = plan.withPlanCritiqueSnapshot(snapshot).withPlanConfidence(confidence);
            planStateStore.update(next);

            Map<String, Object> spread = new LinkedHashMap<>();
            spread.put("planCritiqueError", "");
            spread.put("planReadinessStatus", confidence.getReadinessStatus() != null ? confidence.getReadinessStatus() : "");
            spread.put("planConfidenceLevel", confidence.getLevel() != null ? confidence.getLevel() : "");
            spread.put("planReadinessSummary", confidence.getNotes() != null ? confidence.getNotes() : "");
            spread.put("planCritiqueFindingsJson", JSON.writeValueAsString(findings));
            spread.put("planCritiqueSummary", formatCritiqueSummary(findings));
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
            mergePlanningThreadReview(m, event, state, bind);
            return m;
        }
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
}
