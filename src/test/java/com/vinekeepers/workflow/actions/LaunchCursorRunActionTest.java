package com.vinekeepers.workflow.actions;

import com.vinekeepers.core.cursor.CursorAgentConversation;
import com.vinekeepers.core.cursor.CursorAgentDetails;
import com.vinekeepers.core.cursor.CursorAgentLaunchRequest;
import com.vinekeepers.core.cursor.CursorAgentLaunchResult;
import com.vinekeepers.core.cursor.CursorCloudAdapter;
import com.vinekeepers.core.cursor.CursorCloudException;
import com.vinekeepers.core.cursor.LifecycleRunRecord;
import com.vinekeepers.state.LifecycleContext;
import com.vinekeepers.state.LifecycleContextStore;
import com.vinekeepers.state.StateStore;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.PlanApproval;
import com.vinekeepers.state.planning.PlanApprovalStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchCursorRunActionTest {

    private static final Instant NOW = Instant.parse("2026-03-09T12:00:00Z");

    @Test
    void runReturnsErrorWhenDependenciesNull() {
        StateStore stateStore = new StateStore();
        LifecycleContextStore contextStore = new LifecycleContextStore();
        LaunchCursorRunAction action = new LaunchCursorRunAction(null, stateStore, contextStore);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) action.run(null, Map.of(), Map.of("project", "acme/repo", "codeChange", "Add feature", "__sessionKey", "s1"));
        assertEquals("false", result.get("launchSucceeded"));
        assertTrue(result.get("launchMessage").toString().contains("dependencies not available"));
    }

    @Test
    void runReturnsErrorWhenProjectMissing() {
        CursorCloudAdapter adapter = stubAdapter();
        StateStore stateStore = new StateStore();
        LifecycleContextStore contextStore = new LifecycleContextStore();
        LaunchCursorRunAction action = new LaunchCursorRunAction(adapter, stateStore, contextStore);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) action.run(null, Map.of(), Map.of("codeChange", "Add feature", "__sessionKey", "s1"));
        assertEquals("false", result.get("launchSucceeded"));
        assertEquals("Missing project in state.", result.get("launchMessage"));
    }

    @Test
    void runReturnsErrorWhenCodeChangeMissing() {
        CursorCloudAdapter adapter = stubAdapter();
        StateStore stateStore = new StateStore();
        LifecycleContextStore contextStore = new LifecycleContextStore();
        LaunchCursorRunAction action = new LaunchCursorRunAction(adapter, stateStore, contextStore);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) action.run(null, Map.of(), Map.of("project", "acme/repo", "__sessionKey", "s1"));
        assertEquals("false", result.get("launchSucceeded"));
        assertEquals("Missing feature request in state.", result.get("launchMessage"));
    }

    @Test
    void runReturnsErrorWhenSessionKeyMissing() {
        CursorCloudAdapter adapter = stubAdapter();
        StateStore stateStore = new StateStore();
        LifecycleContextStore contextStore = new LifecycleContextStore();
        LaunchCursorRunAction action = new LaunchCursorRunAction(adapter, stateStore, contextStore);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) action.run(null, Map.of(), Map.of("project", "acme/repo", "codeChange", "Add feature"));
        assertEquals("false", result.get("launchSucceeded"));
        assertEquals("Missing workflow session key.", result.get("launchMessage"));
    }

    @Test
    void runReturnsErrorWhenProjectUnresolvable() {
        CursorCloudAdapter adapter = stubAdapter();
        StateStore stateStore = new StateStore();
        LifecycleContextStore contextStore = new LifecycleContextStore();
        LaunchCursorRunAction action = new LaunchCursorRunAction(adapter, stateStore, contextStore);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) action.run(null, Map.of(), Map.of("project", "local-path", "codeChange", "Add feature", "__sessionKey", "s1"));
        assertEquals("false", result.get("launchSucceeded"));
        assertEquals("Could not resolve project.", result.get("launchMessage"));
    }

    @Test
    void runLaunchesAndStoresRecordAndBindsContext() {
        StateStore stateStore = new StateStore();
        LifecycleContextStore contextStore = new LifecycleContextStore();
        LifecycleContext ctx = new LifecycleContext("ctx-1", "chan-1", NOW);
        contextStore.put(ctx);
        CursorAgentLaunchResult launchResult = new CursorAgentLaunchResult(
                "agent-123", "Luna run", "CREATING",
                "https://github.com/acme/repo", "main", "luna/feature",
                "https://cursor.com/agents?id=agent-123", null, true, NOW);
        CursorCloudAdapter adapter = new CursorCloudAdapter() {
            @Override
            public CursorAgentLaunchResult launchAgent(CursorAgentLaunchRequest request) {
                return launchResult;
            }
            @Override
            public CursorAgentDetails getAgent(String agentId) { return null; }
            @Override
            public CursorAgentConversation getConversation(String agentId) {
                return new CursorAgentConversation(agentId, List.of());
            }
            @Override
            public void addFollowup(String agentId, String promptText) {}
        };
        FeaturePlanStateStore planStore = new FeaturePlanStateStore();
        planStore.put(minPlanWithApproval("ctx-1", "chan-1"));
        LaunchCursorRunAction action = new LaunchCursorRunAction(adapter, stateStore, contextStore, planStore);
        Map<String, Object> bind = Map.of(
                "project", "acme/repo",
                "codeChange", "Add tests",
                "channelId", "chan-1",
                "contextId", "ctx-1",
                "__sessionKey", "bot:luna:conv:chan-1:user-1",
                "__event", Map.of("messageId", "msg-1", "authorId", "user-1"));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) action.run(null, Map.of(), bind);
        assertEquals("true", result.get("launchSucceeded"));
        String msg = result.get("launchMessage").toString();
        assertTrue(msg.contains("Launching Cursor Cloud run"));
        assertTrue(msg.contains("agent-123"));
        assertTrue(msg.contains("Status: launching"), "ack must include status");
        assertTrue(msg.contains("lifecycle room"), "ack must direct user to lifecycle room");
        assertTrue(!msg.contains("updates here"), "acknowledgement must not say updates happen here (intake channel)");
        LifecycleRunRecord record = stateStore.get("cursor:run:agent-123", LifecycleRunRecord.class).orElseThrow();
        assertEquals("agent-123", record.getAgentId());
        assertEquals("chan-1", record.getChannelId());
        assertEquals("msg-1", record.getReplyToMessageId());
        assertEquals("agent-123", stateStore.get("cursor:session:bot:luna:conv:chan-1:user-1:lastRun", String.class).orElse(null));
        assertEquals("agent-123", ctx.getExternalRunId());
        assertNotNull(contextStore.getByExternalRunId("agent-123").orElse(null));
        assertEquals("acme/repo", stateStore.get("luna:lastRepo:user-1", String.class).orElse(null));
        assertEquals("active", contextStore.getByContextId("ctx-1").orElseThrow().getStatus());
    }

    @Test
    void runReturnsErrorWhenContextIdPresentButPlanNotApproved() {
        CursorCloudAdapter adapter = stubAdapter();
        StateStore stateStore = new StateStore();
        LifecycleContextStore contextStore = new LifecycleContextStore();
        FeaturePlanStateStore planStore = new FeaturePlanStateStore();
        planStore.put(minPlanWithApproval("ctx-1", "chan-1").withPlanApproval(null));
        LaunchCursorRunAction action = new LaunchCursorRunAction(adapter, stateStore, contextStore, planStore);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) action.run(null, Map.of(), Map.of(
                "project", "acme/repo",
                "codeChange", "Add feature",
                "contextId", "ctx-1",
                "__sessionKey", "s1"));
        assertEquals("false", result.get("launchSucceeded"));
        assertTrue(result.get("launchMessage").toString().contains("Plan approval required"));
    }

    @Test
    void runReturnsAcknowledgementWithStatusAndAgentUrl() {
        StateStore stateStore = new StateStore();
        LifecycleContextStore contextStore = new LifecycleContextStore();
        CursorAgentLaunchResult launchResult = new CursorAgentLaunchResult(
                "agent-456", "Run", "CREATING",
                "https://github.com/owner/repo", "main", "luna/branch",
                "https://cursor.com/agents?id=agent-456", null, true, NOW);
        CursorCloudAdapter adapter = new CursorCloudAdapter() {
            @Override
            public CursorAgentLaunchResult launchAgent(CursorAgentLaunchRequest request) { return launchResult; }
            @Override
            public CursorAgentDetails getAgent(String agentId) { return null; }
            @Override
            public CursorAgentConversation getConversation(String agentId) {
                return new CursorAgentConversation(agentId, List.of());
            }
            @Override
            public void addFollowup(String agentId, String promptText) {}
        };
        LaunchCursorRunAction action = new LaunchCursorRunAction(adapter, stateStore, contextStore);
        Object result = action.run(null, Map.of(), Map.of(
                "project", "owner/repo", "codeChange", "Add feature",
                "__sessionKey", "skey"));
        String ack = result.toString();
        assertTrue(ack.contains("Launching Cursor Cloud run"), "ack must mention launch");
        assertTrue(ack.contains("Status: launching"), "ack must include status");
        assertTrue(ack.contains("lifecycle room"), "ack must mention lifecycle room");
        assertTrue(ack.contains("Agent: "), "ack must include agent URL when present");
    }

    @Test
    void runReturnsMessageOnCursorCloudException() {
        CursorCloudAdapter adapter = new CursorCloudAdapter() {
            @Override
            public CursorAgentLaunchResult launchAgent(CursorAgentLaunchRequest request) {
                throw new CursorCloudException("API error");
            }
            @Override
            public CursorAgentDetails getAgent(String agentId) { return null; }
            @Override
            public CursorAgentConversation getConversation(String agentId) {
                return new CursorAgentConversation(agentId, List.of());
            }
            @Override
            public void addFollowup(String agentId, String promptText) {}
        };
        StateStore stateStore = new StateStore();
        LifecycleContextStore contextStore = new LifecycleContextStore();
        LaunchCursorRunAction action = new LaunchCursorRunAction(adapter, stateStore, contextStore);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) action.run(null, Map.of(), Map.of(
                "project", "acme/repo", "codeChange", "Add feature", "__sessionKey", "s1"));
        assertEquals("false", result.get("launchSucceeded"));
        assertTrue(result.get("launchMessage").toString().startsWith("Cursor launch failed:"));
    }

    private static FeaturePlanState minPlanWithApproval(String contextId, String roomChannelId) {
        return new FeaturePlanState(
                contextId,
                "f1",
                "s",
                roomChannelId,
                null,
                null,
                "t",
                null,
                "PLANNING",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                FeaturePlanState.initialSectionStatuses(),
                null,
                new PlanApproval(PlanApprovalStatus.APPROVE, "u1", NOW),
                null,
                null,
                null,
                null,
                null,
                null,
                java.util.Map.of(),
                null,
                null);
    }

    private static CursorCloudAdapter stubAdapter() {
        return new CursorCloudAdapter() {
            @Override
            public CursorAgentLaunchResult launchAgent(CursorAgentLaunchRequest req) {
                return new CursorAgentLaunchResult(
                        "stub", "Run", "CREATING",
                        req.repositoryUrl(), req.baseRef(), req.branchName(),
                        "https://cursor.com/agents?id=stub", null, true, NOW);
            }
            @Override
            public CursorAgentDetails getAgent(String agentId) { return null; }
            @Override
            public CursorAgentConversation getConversation(String agentId) {
                return new CursorAgentConversation(agentId, List.of());
            }
            @Override
            public void addFollowup(String agentId, String promptText) {}
        };
    }
}
