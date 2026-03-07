package com.vinekeepers.workflow;

/**
 * Per-conversation state for the Luna bot gathering workflow.
 * Persisted via StateStore keyed by conversation (e.g. Discord channel id).
 */
public final class GatheringState {

    public enum Step {
        /** Waiting for user to specify which project to update */
        AWAITING_PROJECT,
        /** Waiting for user to specify what code change to make */
        AWAITING_CHANGE,
        /** Ready to invoke Cursor Cloud API (branch, nova-commit, push, optional PR) */
        READY_TO_RUN,
        /** Run completed */
        DONE
    }

    private final Step step;
    private final String project;
    private final String codeChangeDescription;

    public GatheringState(Step step, String project, String codeChangeDescription) {
        this.step = step != null ? step : Step.AWAITING_PROJECT;
        this.project = project;
        this.codeChangeDescription = codeChangeDescription;
    }

    public static GatheringState initial() {
        return new GatheringState(Step.AWAITING_PROJECT, null, null);
    }

    public Step getStep() {
        return step;
    }

    public String getProject() {
        return project;
    }

    public String getCodeChangeDescription() {
        return codeChangeDescription;
    }

    public GatheringState withProject(String project) {
        return new GatheringState(Step.AWAITING_CHANGE, project, codeChangeDescription);
    }

    public GatheringState withCodeChange(String codeChangeDescription) {
        return new GatheringState(Step.READY_TO_RUN, project, codeChangeDescription);
    }

    public GatheringState withStep(Step step) {
        return new GatheringState(step, project, codeChangeDescription);
    }
}
