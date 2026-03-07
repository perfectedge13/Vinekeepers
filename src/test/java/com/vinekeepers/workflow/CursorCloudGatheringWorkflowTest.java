package com.vinekeepers.workflow;

import com.vinekeepers.core.cursor.CursorCloudAdapter;
import com.vinekeepers.events.Event;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CursorCloudGatheringWorkflowTest {

    private static final CursorCloudAdapter NO_OP_ADAPTER = new CursorCloudAdapter() {
        @Override public String createBranch(String p, String b) { return ""; }
        @Override public String runNovaCommit(String p, String c) { return ""; }
        @Override public String push(String p) { return ""; }
        @Override public String createPr(String p, String t) { return ""; }
    };

    private static Event eventWithContent(String content) {
        return new Event("discord:default", "message", Map.of("content", content));
    }

    private static Event eventWithText(String text) {
        return new Event("discord:default", "message", Map.of("text", text));
    }

    @Test
    void awaitingProjectWithNoInputPromptsForProject() {
        CursorCloudGatheringWorkflow workflow = new CursorCloudGatheringWorkflow(NO_OP_ADAPTER);
        WorkflowResult<GatheringState> result = workflow.process(eventWithContent(""), GatheringState.initial());
        assertFalse(result.isDone());
        assertTrue(result.getMessage().contains("project"));
        assertEquals(GatheringState.Step.AWAITING_PROJECT, result.getState().getStep());
    }

    @Test
    void awaitingProjectWithProjectAdvancesToAwaitingChange() {
        CursorCloudGatheringWorkflow workflow = new CursorCloudGatheringWorkflow(NO_OP_ADAPTER);
        WorkflowResult<GatheringState> result = workflow.process(eventWithContent("my-repo"), GatheringState.initial());
        assertFalse(result.isDone());
        assertTrue(result.getMessage().contains("code change"));
        assertEquals(GatheringState.Step.AWAITING_CHANGE, result.getState().getStep());
        assertEquals("my-repo", result.getState().getProject());
    }

    @Test
    void awaitingChangeWithNoInputPromptsForChange() {
        GatheringState state = GatheringState.initial().withProject("p");
        CursorCloudGatheringWorkflow workflow = new CursorCloudGatheringWorkflow(NO_OP_ADAPTER);
        WorkflowResult<GatheringState> result = workflow.process(eventWithContent(""), state);
        assertFalse(result.isDone());
        assertTrue(result.getMessage().toLowerCase().contains("change"));
        assertEquals(GatheringState.Step.AWAITING_CHANGE, result.getState().getStep());
    }

    @Test
    void awaitingChangeWithInputRunsAdapterAndCompletes() {
        CursorCloudAdapter stub = new CursorCloudAdapter() {
            @Override
            public String createBranch(String projectPathOrId, String branchName) {
                return "Branch:" + branchName;
            }
            @Override
            public String runNovaCommit(String projectPathOrId, String changeDescription) {
                return "Commit done";
            }
            @Override
            public String push(String projectPathOrId) {
                return "Pushed";
            }
            @Override
            public String createPr(String projectPathOrId, String title) {
                return "PR:" + title;
            }
        };
        GatheringState state = GatheringState.initial().withProject("proj").withCodeChange("add tests");
        CursorCloudGatheringWorkflow workflow = new CursorCloudGatheringWorkflow(stub);
        WorkflowResult<GatheringState> result = workflow.process(eventWithContent("add tests"), state);
        assertTrue(result.isDone());
        assertTrue(result.getMessage().contains("Branch:"));
        assertTrue(result.getMessage().contains("Commit done"));
        assertTrue(result.getMessage().contains("Pushed"));
        assertTrue(result.getMessage().contains("PR:"));
        assertEquals(GatheringState.Step.DONE, result.getState().getStep());
    }

    @Test
    void doneStepReturnsSessionCompleteMessage() {
        GatheringState doneState = GatheringState.initial().withStep(GatheringState.Step.DONE);
        CursorCloudGatheringWorkflow workflow = new CursorCloudGatheringWorkflow(NO_OP_ADAPTER);
        WorkflowResult<GatheringState> result = workflow.process(eventWithContent("hi"), doneState);
        assertTrue(result.isDone());
        assertTrue(result.getMessage().toLowerCase().contains("complete"));
    }

    @Test
    void nullStateTreatedAsInitial() {
        CursorCloudGatheringWorkflow workflow = new CursorCloudGatheringWorkflow(NO_OP_ADAPTER);
        WorkflowResult<GatheringState> result = workflow.process(eventWithContent(""), null);
        assertEquals(GatheringState.Step.AWAITING_PROJECT, result.getState().getStep());
    }

    @Test
    void getUserMessageFromTextOrContent() {
        Event withText = new Event("x", "message", Map.of("text", "from-text"));
        CursorCloudGatheringWorkflow workflow = new CursorCloudGatheringWorkflow(NO_OP_ADAPTER);
        WorkflowResult<GatheringState> r = workflow.process(withText, null);
        assertEquals("from-text", r.getState().getProject());
        Event withContent = new Event("x", "message", Map.of("content", "from-content"));
        WorkflowResult<GatheringState> r2 = workflow.process(withContent, GatheringState.initial());
        assertEquals("from-content", r2.getState().getProject());
    }
}
