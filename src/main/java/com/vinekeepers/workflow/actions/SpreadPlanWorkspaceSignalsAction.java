package com.vinekeepers.workflow.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.repo.RepoWorkspaceStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads {@link FeaturePlanState} workspace linkage and spreads workflow branch keys after
 * {@link EnsureRepoWorkspaceAction}.
 */
public final class SpreadPlanWorkspaceSignalsAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final FeaturePlanStateStore planStateStore;

    public SpreadPlanWorkspaceSignalsAction(FeaturePlanStateStore planStateStore) {
        this.planStateStore = planStateStore;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("repoWorkspaceReady", "false");
        out.put("repoLocalPathPresent", "false");
        out.put("repoWorkspaceStatusSummary", "No plan context.");
        if (planStateStore == null) {
            return out;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            out.put("repoWorkspaceStatusSummary", "Missing contextId.");
            return out;
        }
        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            out.put("repoWorkspaceStatusSummary", "No plan for context.");
            return out;
        }
        String statusName = plan.getRepoWorkspaceStatus() != null ? plan.getRepoWorkspaceStatus().trim() : "";
        boolean materialized = isMaterialized(statusName);
        String local = plan.getRepoLocalPath();
        boolean pathOk = local != null && !local.isBlank();
        out.put("repoWorkspaceReady", materialized ? "true" : "false");
        out.put("repoLocalPathPresent", pathOk ? "true" : "false");
        String notes = plan.getRepoAccessNotes() != null ? plan.getRepoAccessNotes().trim() : "";
        StringBuilder sb = new StringBuilder();
        sb.append("Workspace status: ").append(statusName.isBlank() ? "unknown" : statusName);
        if (pathOk) {
            sb.append("; local path recorded.");
        } else {
            sb.append("; no local clone path (exploration will use request text only).");
        }
        if (!notes.isBlank()) {
            sb.append(" Notes: ").append(truncate(notes, 280));
        }
        out.put("repoWorkspaceStatusSummary", sb.toString());
        try {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("workspaceStatus", statusName.isBlank() ? "unknown" : statusName);
            evidence.put("localPathPresent", pathOk);
            evidence.put("repoRef", plan.getRepoRef() != null ? plan.getRepoRef() : "");
            if (!notes.isBlank()) {
                evidence.put("accessNotes", notes);
            }
            out.put("planningRepoEvidenceJson", JSON.writeValueAsString(evidence));
        } catch (Exception e) {
            out.put("planningRepoEvidenceJson", "{}");
        }
        return out;
    }

    private static boolean isMaterialized(String statusName) {
        if (statusName == null || statusName.isBlank()) {
            return false;
        }
        try {
            RepoWorkspaceStatus s = RepoWorkspaceStatus.valueOf(statusName.trim().toUpperCase());
            return s == RepoWorkspaceStatus.MATERIALIZED || s == RepoWorkspaceStatus.RESOLVED_LOCAL;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
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
