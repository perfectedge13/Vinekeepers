package com.vinekeepers.workflow.planning;

import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.repo.RepoWorkspaceStatus;

/**
 * Workspace-only preflight before silent synthesis: repo materialization failures are not planning gaps and do not use
 * clarification merge.
 */
public final class PlanningWorkspacePreflightService {

    public enum PreflightStatus {
        READY,
        DEGRADED,
        BLOCKED
    }

    public record PreflightResult(PreflightStatus status, String userPrompt) {
        public boolean blocked() {
            return status == PreflightStatus.BLOCKED;
        }
    }

    private PlanningWorkspacePreflightService() {}

    public static PreflightResult evaluate(FeaturePlanState plan) {
        if (plan == null) {
            return new PreflightResult(PreflightStatus.READY, "");
        }
        String raw = plan.getRepoWorkspaceStatus();
        if (raw == null || raw.isBlank()) {
            return new PreflightResult(PreflightStatus.READY, "");
        }
        try {
            RepoWorkspaceStatus s = RepoWorkspaceStatus.valueOf(raw.trim());
            if (s == RepoWorkspaceStatus.FAILED || s == RepoWorkspaceStatus.UNAVAILABLE) {
                String wsReason =
                        "We could not prepare a local workspace for this repository ("
                                + plan.getRepoWorkspaceStatus()
                                + "). "
                                + (plan.getRepoAccessNotes() != null && !plan.getRepoAccessNotes().isBlank()
                                        ? plan.getRepoAccessNotes() + " "
                                        : "")
                                + "Reply with access notes, a local checkout path, or how you want to proceed.";
                return new PreflightResult(PreflightStatus.BLOCKED, wsReason);
            }
        } catch (IllegalArgumentException e) {
            return new PreflightResult(PreflightStatus.DEGRADED, "");
        }
        String local = plan.getRepoLocalPath();
        if (local == null || local.isBlank()) {
            return new PreflightResult(PreflightStatus.DEGRADED, "");
        }
        return new PreflightResult(PreflightStatus.READY, "");
    }
}
