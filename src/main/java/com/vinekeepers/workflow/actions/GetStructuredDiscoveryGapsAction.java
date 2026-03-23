package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.DiscoveryGap;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.discovery.StructuredDiscoverySupport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Returns a map of workflow state keys (for {@code storeSpread}) with JSON gaps and branch flags.
 */
public final class GetStructuredDiscoveryGapsAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeaturePlanStateStore planStateStore;
    private final WorkProfileRegistry workProfileRegistry;

    public GetStructuredDiscoveryGapsAction(FeaturePlanStateStore planStateStore, WorkProfileRegistry workProfileRegistry) {
        this.planStateStore = planStateStore;
        this.workProfileRegistry = workProfileRegistry;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> err = new LinkedHashMap<>();
        if (planStateStore == null || workProfileRegistry == null) {
            err.put("discoveryGapsJson", "[]");
            err.put("discoveryHasOpenGaps", "false");
            err.put("discoveryBlockingIssueMode", "false");
            err.put("discoveryScanError", "Plan store or work profile registry not available.");
            return err;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            err.put("discoveryGapsJson", "[]");
            err.put("discoveryHasOpenGaps", "false");
            err.put("discoveryBlockingIssueMode", "false");
            err.put("discoveryScanError", "Missing contextId for get_structured_discovery_gaps.");
            return err;
        }
        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            err.put("discoveryGapsJson", "[]");
            err.put("discoveryHasOpenGaps", "false");
            err.put("discoveryBlockingIssueMode", "false");
            err.put("discoveryScanError", "No FeaturePlanState for contextId: " + contextId);
            return err;
        }
        String profileId = plan.getProfileId();
        if (profileId == null || profileId.isBlank()) {
            err.put("discoveryGapsJson", "[]");
            err.put("discoveryHasOpenGaps", "false");
            err.put("discoveryBlockingIssueMode", "false");
            err.put("discoveryScanError", "FeaturePlanState has no profileId.");
            return err;
        }
        WorkProfileDefinition profile = workProfileRegistry.get(profileId).orElse(null);
        if (profile == null) {
            err.put("discoveryGapsJson", "[]");
            err.put("discoveryHasOpenGaps", "false");
            err.put("discoveryBlockingIssueMode", "false");
            err.put("discoveryScanError", "Unknown work profile: " + profileId);
            return err;
        }
        try {
            boolean firstAutonomousDone =
                    "true".equalsIgnoreCase(getString(state, "planningAutonomousFirstPassCompleted"))
                            || plan.isAutonomousPlanningPassCompleted();
            List<DiscoveryGap> gaps =
                    StructuredDiscoverySupport.collectGaps(plan, profile, !firstAutonomousDone);
            return StructuredDiscoverySupport.spreadFromGaps(gaps);
        } catch (Exception e) {
            err.put("discoveryGapsJson", "[]");
            err.put("discoveryHasOpenGaps", "false");
            err.put("discoveryBlockingIssueMode", "false");
            err.put("discoveryScanError", e.getMessage() != null ? e.getMessage() : "discovery scan failed");
            return err;
        }
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
