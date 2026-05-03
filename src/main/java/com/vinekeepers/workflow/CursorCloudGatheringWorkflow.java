package com.vinekeepers.workflow;

import com.vinekeepers.core.cursor.CursorCloudAdapter;
import com.vinekeepers.core.cursor.CursorAgentLaunchRequest;
import com.vinekeepers.core.cursor.CursorAgentLaunchResult;
import com.vinekeepers.events.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Legacy multi-turn workflow for Luna retained for tests and backwards compatibility.
 */
public final class CursorCloudGatheringWorkflow implements Workflow<GatheringState> {

    private static final Logger log = LoggerFactory.getLogger(CursorCloudGatheringWorkflow.class);

    private final CursorCloudAdapter cursorAdapter;

    public CursorCloudGatheringWorkflow(CursorCloudAdapter cursorAdapter) {
        this.cursorAdapter = cursorAdapter != null ? cursorAdapter : new com.vinekeepers.core.cursor.CursorCloudAdapterImpl();
    }

    @Override
    public WorkflowResult<GatheringState> process(Event event, GatheringState state) {
        GatheringState current = state != null ? state : GatheringState.initial();
        String userInput = getUserMessage(event);

        switch (current.getStep()) {
            case AWAITING_PROJECT:
                if (userInput != null && !userInput.isBlank()) {
                    GatheringState next = current.withProject(userInput.trim());
                    return WorkflowResult.continueWith(next, "What code change should I make?");
                }
                return WorkflowResult.continueWith(current, "Which project do you want to update? (reply with project path or name)");

            case AWAITING_CHANGE:
                if (userInput != null && !userInput.isBlank()) {
                    GatheringState withChange = current.withCodeChange(userInput.trim());
                    return runCursorAndFinish(withChange);
                }
                return WorkflowResult.continueWith(current, "Describe the code change you want me to make.");

            case READY_TO_RUN:
                return runCursorAndFinish(current);

            case DONE:
                return WorkflowResult.done(current, "Session complete. Use /Luna again to start a new request.");

            default:
                return WorkflowResult.continueWith(GatheringState.initial(), "Which project do you want to update?");
        }
    }

    private WorkflowResult<GatheringState> runCursorAndFinish(GatheringState state) {
        String project = state.getProject();
        String change = state.getCodeChangeDescription();
        if (project == null || project.isBlank()) {
            return WorkflowResult.continueWith(state.withStep(GatheringState.Step.AWAITING_PROJECT), "Which project do you want to update?");
        }
        if (change == null || change.isBlank()) {
            return WorkflowResult.continueWith(state.withStep(GatheringState.Step.AWAITING_CHANGE), "Describe the code change you want.");
        }
        try {
            CursorAgentLaunchResult launch = cursorAdapter.launchAgent(new CursorAgentLaunchRequest(
                    change,
                    project,
                    "main",
                    "luna/legacy",
                    true,
                    null
            ));
            GatheringState done = state.withStep(GatheringState.Step.DONE);
            return WorkflowResult.done(done, "Launched Cursor agent " + launch.id());
        } catch (Exception e) {
            log.warn("Cursor adapter error", e);
            GatheringState done = state.withStep(GatheringState.Step.DONE);
            return WorkflowResult.done(done, "Error: " + e.getMessage());
        }
    }

    private static String getUserMessage(Event event) {
        if (event == null || event.getPayload() == null) return null;
        String text = event.getPayload("text", String.class);
        if (text != null) return text;
        text = event.getPayload("content", String.class);
        return text;
    }
}
