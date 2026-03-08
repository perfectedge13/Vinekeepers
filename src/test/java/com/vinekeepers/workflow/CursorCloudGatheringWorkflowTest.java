package com.vinekeepers.workflow;

import com.vinekeepers.core.cursor.CursorAgentConversation;
import com.vinekeepers.core.cursor.CursorAgentDetails;
import com.vinekeepers.core.cursor.CursorAgentLaunchRequest;
import com.vinekeepers.core.cursor.CursorAgentLaunchResult;
import com.vinekeepers.core.cursor.CursorAgentMessage;
import com.vinekeepers.core.cursor.CursorCloudAdapter;
import com.vinekeepers.events.Event;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CursorCloudGatheringWorkflowTest {

    private static final CursorCloudAdapter NO_OP_ADAPTER = new CursorCloudAdapter() {
        @Override
        public CursorAgentLaunchResult launchAgent(CursorAgentLaunchRequest request) {
            return new CursorAgentLaunchResult("bc_1", "Luna", "CREATING",
                    request.repositoryUrl(), request.baseRef(), request.branchName(),
                    "https://cursor.com/agents?id=bc_1", null, true, Instant.now());
        }

        @Override
        public CursorAgentDetails getAgent(String agentId) {
            return new CursorAgentDetails(agentId, "Luna", "RUNNING", null, null, null, null, null, null, Instant.now());
        }

        @Override
        public CursorAgentConversation getConversation(String agentId) {
            return new CursorAgentConversation(agentId, List.of(new CursorAgentMessage("m1", "assistant_message", "Working")));
        }

        @Override
        public void addFollowup(String agentId, String promptText) {
        }
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
            public CursorAgentLaunchResult launchAgent(CursorAgentLaunchRequest request) {
                return new CursorAgentLaunchResult(
                        "bc_42",
                        "Luna feature",
                        "CREATING",
                        request.repositoryUrl(),
                        request.baseRef(),
                        request.branchName(),
                        "https://cursor.com/agents?id=bc_42",
                        null,
                        true,
                        Instant.now()
                );
            }

            @Override
            public CursorAgentDetails getAgent(String agentId) {
                return new CursorAgentDetails(agentId, "Luna", "RUNNING", null, null, null, null, null, null, Instant.now());
            }

            @Override
            public CursorAgentConversation getConversation(String agentId) {
                return new CursorAgentConversation(agentId, List.of());
            }

            @Override
            public void addFollowup(String agentId, String promptText) {
            }
        };
        GatheringState state = GatheringState.initial().withProject("proj").withCodeChange("add tests");
        CursorCloudGatheringWorkflow workflow = new CursorCloudGatheringWorkflow(stub);
        WorkflowResult<GatheringState> result = workflow.process(eventWithContent("add tests"), state);
        assertTrue(result.isDone());
        assertTrue(result.getMessage().contains("bc_42"));
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
