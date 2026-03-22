package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.planning.PlanGovernanceDeriver;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Merges discovery gaps and artifact signals into {@link FeaturePlanState} governance lists (deterministic).
 */
public final class DerivePlanGovernanceAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStateStore;
    private final WorkProfileRegistry workProfileRegistry;

    public DerivePlanGovernanceAction(
            FeaturePlanStateStore planStateStore, WorkProfileRegistry workProfileRegistry) {
        this.planStateStore = planStateStore;
        this.workProfileRegistry = workProfileRegistry;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("planGovernanceDeriveError", "");
        spread.put("planGovernanceDeriveOk", "false");
        if (planStateStore == null || workProfileRegistry == null) {
            spread.put("planGovernanceDeriveError", "Plan store or work profile registry not available.");
            return spread;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            spread.put("planGovernanceDeriveError", "Missing contextId.");
            return spread;
        }
        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            spread.put("planGovernanceDeriveError", "No FeaturePlanState for contextId.");
            return spread;
        }
        String profileId = plan.getProfileId();
        if (profileId == null || profileId.isBlank()) {
            spread.put("planGovernanceDeriveError", "Plan has no profileId.");
            return spread;
        }
        WorkProfileDefinition profile = workProfileRegistry.get(profileId).orElse(null);
        if (profile == null) {
            spread.put("planGovernanceDeriveError", "Unknown profileId: " + profileId);
            return spread;
        }
        FeaturePlanState next = PlanGovernanceDeriver.derive(plan, profile);
        planStateStore.update(next);
        spread.put("planGovernanceDeriveOk", "true");
        spread.put("planGovernanceIssueCount", String.valueOf(next.getIssues().size()));
        spread.put("planGovernanceAssumptionCount", String.valueOf(next.getAssumptions().size()));
        return spread;
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
