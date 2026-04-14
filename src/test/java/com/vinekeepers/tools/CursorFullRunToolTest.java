package com.vinekeepers.tools;

import com.vinekeepers.core.cursor.CursorAgentConversation;
import com.vinekeepers.core.cursor.CursorAgentDetails;
import com.vinekeepers.core.cursor.CursorAgentLaunchRequest;
import com.vinekeepers.core.cursor.CursorAgentLaunchResult;
import com.vinekeepers.core.cursor.CursorAgentMessage;
import com.vinekeepers.core.cursor.CursorCloudAdapter;
import com.vinekeepers.core.cursor.LifecycleRunRecord;
import com.vinekeepers.state.StateStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CursorFullRunToolTest {

    @Test
    void runLaunchesAgentAndStoresRunState() {
        AtomicReference<CursorAgentLaunchRequest> capturedRequest = new AtomicReference<>();
        CursorCloudAdapter adapter = new CursorCloudAdapter() {
            @Override
            public CursorAgentLaunchResult launchAgent(CursorAgentLaunchRequest request) {
                capturedRequest.set(request);
                return new CursorAgentLaunchResult(
                        "bc_999",
                        "Luna run",
                        "CREATING",
                        request.repositoryUrl(),
                        request.baseRef(),
                        request.branchName(),
                        "https://cursor.com/agents?id=bc_999",
                        null,
                        true,
                        Instant.parse("2026-03-07T20:00:00Z")
                );
            }

            @Override
            public CursorAgentDetails getAgent(String agentId) {
                return new CursorAgentDetails(agentId, "", "", "", "", "", "", "", "", Instant.now());
            }

            @Override
            public CursorAgentConversation getConversation(String agentId) {
                return new CursorAgentConversation(agentId, List.of(new CursorAgentMessage("m1", "assistant_message", "Working")));
            }

            @Override
            public void addFollowup(String agentId, String promptText) {
            }
        };
        StateStore stateStore = new StateStore();
        CursorFullRunTool tool = new CursorFullRunTool(adapter, stateStore);

        Object result = tool.run(Map.of(
                "project", "acme/vinekeepers",
                "codeChange", "Add a cloud integration test",
                "__sessionKey", "bot:luna:conv:chan-1:user-1",
                "__event", Map.of("channelId", "chan-1", "messageId", "msg-1")
        ));

        assertTrue(result.toString().contains("Launching Cursor Cloud run"));
        assertEquals("https://github.com/acme/vinekeepers", capturedRequest.get().repositoryUrl());
        LifecycleRunRecord runState = stateStore.get("cursor:run:bc_999", LifecycleRunRecord.class).orElseThrow();
        assertEquals("bc_999", runState.getAgentId());
        assertEquals("chan-1", runState.getChannelId());
        assertEquals("msg-1", runState.getReplyToMessageId());
    }

    @Test
    void runRejectsUnresolvableProject() {
        CursorFullRunTool tool = new CursorFullRunTool(new CursorCloudAdapter() {
            @Override
            public CursorAgentLaunchResult launchAgent(CursorAgentLaunchRequest request) {
                throw new AssertionError("should not launch");
            }

            @Override
            public CursorAgentDetails getAgent(String agentId) {
                throw new AssertionError("should not query");
            }

            @Override
            public CursorAgentConversation getConversation(String agentId) {
                throw new AssertionError("should not query");
            }

            @Override
            public void addFollowup(String agentId, String promptText) {
                throw new AssertionError("should not follow up");
            }
        }, new StateStore());

        Object result = tool.run(Map.of(
                "project", "local-folder",
                "codeChange", "Ship it",
                "__sessionKey", "session"
        ));

        assertTrue(result.toString().contains("Could not resolve project"));
    }

    @Test
    void runUsesModelFromArgsWhenProvided() {
        AtomicReference<CursorAgentLaunchRequest> capturedRequest = new AtomicReference<>();
        CursorCloudAdapter adapter = new CursorCloudAdapter() {
            @Override
            public CursorAgentLaunchResult launchAgent(CursorAgentLaunchRequest request) {
                capturedRequest.set(request);
                return new CursorAgentLaunchResult(
                        "bc_1000",
                        "Luna run",
                        "CREATING",
                        request.repositoryUrl(),
                        request.baseRef(),
                        request.branchName(),
                        "https://cursor.com/agents?id=bc_1000",
                        null,
                        true,
                        Instant.parse("2026-03-07T20:00:00Z")
                );
            }

            @Override
            public CursorAgentDetails getAgent(String agentId) {
                return new CursorAgentDetails(agentId, "", "", "", "", "", "", "", "", Instant.now());
            }

            @Override
            public CursorAgentConversation getConversation(String agentId) {
                return new CursorAgentConversation(agentId, List.of(new CursorAgentMessage("m1", "assistant_message", "Working")));
            }

            @Override
            public void addFollowup(String agentId, String promptText) {
            }
        };
        StateStore stateStore = new StateStore();
        CursorFullRunTool tool = new CursorFullRunTool(adapter, stateStore);

        tool.run(Map.of(
                "project", "acme/vinekeepers",
                "codeChange", "Add model override",
                "model", "gpt-4.1-mini",
                "__sessionKey", "bot:luna:conv:chan-1:user-1",
                "__event", Map.of("channelId", "chan-1", "messageId", "msg-1")
        ));

        assertEquals("gpt-4.1-mini", capturedRequest.get().model());
    }
}
