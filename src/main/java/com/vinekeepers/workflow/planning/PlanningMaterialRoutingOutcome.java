package com.vinekeepers.workflow.planning;

/**
 * Material-cycle pacing signals after drafting (redraft / post / block notices). Production routing uses this type —
 * not {@link PlanningPostDraftGovernor.Result} — alongside {@link com.vinekeepers.state.planning.PlanningCanonicalDecision}.
 */
public record PlanningMaterialRoutingOutcome(
        PlanningPostDraftAction action,
        PlanningPostDraftGovernor.LoopOutcome outcome,
        String noticeMarkdown,
        boolean forceUserInputRequired,
        boolean userInputRequired,
        boolean readyToPostPacket,
        String planningPhase,
        boolean revisionNeeded) {

    public boolean packetPostingAllowed() {
        return outcome == PlanningPostDraftGovernor.LoopOutcome.POST
                || outcome == PlanningPostDraftGovernor.LoopOutcome.ASSUME;
    }

    /** For deprecated {@link PlanningPostDraftGovernor#derive} tests only. */
    public PlanningPostDraftGovernor.Result toLegacyResult() {
        return new PlanningPostDraftGovernor.Result(
                action,
                outcome,
                noticeMarkdown,
                forceUserInputRequired,
                userInputRequired,
                readyToPostPacket,
                planningPhase,
                revisionNeeded);
    }

    public static PlanningMaterialRoutingOutcome fromLegacyResult(PlanningPostDraftGovernor.Result r) {
        if (r == null) {
            return new PlanningMaterialRoutingOutcome(
                    null,
                    PlanningPostDraftGovernor.LoopOutcome.BLOCK,
                    "",
                    false,
                    false,
                    false,
                    "FAILED",
                    false);
        }
        return new PlanningMaterialRoutingOutcome(
                r.action(),
                r.outcome(),
                r.noticeMarkdown(),
                r.forceUserInputRequired(),
                r.userInputRequired(),
                r.readyToPostPacket(),
                r.planningPhase(),
                r.revisionNeeded());
    }
}
