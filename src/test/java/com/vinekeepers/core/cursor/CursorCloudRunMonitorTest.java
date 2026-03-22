package com.vinekeepers.core.cursor;

import com.vinekeepers.connectors.ReplySender;
import com.vinekeepers.state.StateStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CursorCloudRunMonitorTest {

    @Test
    void tickSendsStatusAndAssistantUpdates() {
        CursorCloudAdapter adapter = new CursorCloudAdapter() {
            @Override
            public CursorAgentLaunchResult launchAgent(CursorAgentLaunchRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CursorAgentDetails getAgent(String agentId) {
                return new CursorAgentDetails(
                        agentId,
                        "Luna run",
                        "RUNNING",
                        "https://github.com/acme/vinekeepers",
                        "main",
                        "luna/add-tests",
                        "https://cursor.com/agents?id=" + agentId,
                        null,
                        null,
                        Instant.now()
                );
            }

            @Override
            public CursorAgentConversation getConversation(String agentId) {
                return new CursorAgentConversation(agentId, List.of(
                        new CursorAgentMessage("msg_1", "assistant_message", "Reviewing specs now")
                ));
            }

            @Override
            public void addFollowup(String agentId, String promptText) {
                throw new UnsupportedOperationException();
            }
        };
        StateStore store = new StateStore();
        LifecycleRunRecord runState = new LifecycleRunRecord(
                "bc_1", "session", "acme/vinekeepers", "https://github.com/acme/vinekeepers",
                "main", "luna/add-tests", "https://cursor.com/agents?id=bc_1",
                "Ship it", "chan-1", "msg-1", Instant.now(), "CREATING");
        store.put("cursor:run:bc_1", runState);
        CursorCloudRunMonitor monitor = new CursorCloudRunMonitor(adapter, store, 1000);
        List<String> messages = new ArrayList<>();
        ReplySender sender = (channelId, messageId, content) -> messages.add(content);
        monitor.setReplySender(sender);

        monitor.tick();

        assertEquals(2, messages.size());
        assertTrue(messages.getFirst().contains("RUNNING"));
        assertTrue(messages.get(1).contains("Reviewing specs now"));
        monitor.close();
    }

    @Test
    void tickSendsTerminalUpdateOnce() {
        CursorCloudAdapter adapter = new CursorCloudAdapter() {
            @Override
            public CursorAgentLaunchResult launchAgent(CursorAgentLaunchRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CursorAgentDetails getAgent(String agentId) {
                return new CursorAgentDetails(
                        agentId,
                        "Luna run",
                        "FINISHED",
                        "https://github.com/acme/vinekeepers",
                        "main",
                        "luna/add-tests",
                        "https://cursor.com/agents?id=" + agentId,
                        "https://github.com/acme/vinekeepers/pull/12",
                        "Added the requested feature",
                        Instant.now()
                );
            }

            @Override
            public CursorAgentConversation getConversation(String agentId) {
                return new CursorAgentConversation(agentId, List.of());
            }

            @Override
            public void addFollowup(String agentId, String promptText) {
                throw new UnsupportedOperationException();
            }
        };
        StateStore store = new StateStore();
        LifecycleRunRecord runState = new LifecycleRunRecord(
                "bc_2", "session", "acme/vinekeepers", "https://github.com/acme/vinekeepers",
                "main", "luna/add-tests", "https://cursor.com/agents?id=bc_2",
                "Ship it", "chan-1", "msg-1", Instant.now(), "RUNNING");
        store.put("cursor:run:bc_2", runState);
        CursorCloudRunMonitor monitor = new CursorCloudRunMonitor(adapter, store, 1000);
        List<String> messages = new ArrayList<>();
        monitor.setReplySender((channelId, messageId, content) -> messages.add(content));

        monitor.tick();
        monitor.tick();

        assertEquals(3, messages.size());
        assertTrue(messages.stream().anyMatch(m -> m.contains("pull/12")));
        assertTrue(messages.getLast().contains("finished") || messages.getLast().contains("Finished"));
        monitor.close();
    }

    @Test
    void tickSuppressesDuplicateAssistantTextAcrossMessageIds() {
        CursorCloudAdapter adapter = new CursorCloudAdapter() {
            private int seq;

            @Override
            public CursorAgentLaunchResult launchAgent(CursorAgentLaunchRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CursorAgentDetails getAgent(String agentId) {
                return new CursorAgentDetails(
                        agentId,
                        "Run",
                        "RUNNING",
                        "https://github.com/acme/repo",
                        "main",
                        "br",
                        "https://cursor.com/agents?id=" + agentId,
                        null,
                        null,
                        Instant.now());
            }

            @Override
            public CursorAgentConversation getConversation(String agentId) {
                seq++;
                String id = "msg_" + seq;
                return new CursorAgentConversation(agentId, List.of(
                        new CursorAgentMessage(id, "assistant_message", "Same status update text")));
            }

            @Override
            public void addFollowup(String agentId, String promptText) {
                throw new UnsupportedOperationException();
            }
        };
        StateStore store = new StateStore();
        LifecycleRunRecord runState = new LifecycleRunRecord(
                "bc_dedupe", "session", "acme/repo", "https://github.com/acme/repo",
                "main", "br", "https://cursor.com/agents?id=bc_dedupe",
                "Req", "chan-1", "msg-1", Instant.now(), "RUNNING");
        store.put("cursor:run:bc_dedupe", runState);
        CursorCloudRunMonitor monitor = new CursorCloudRunMonitor(adapter, store, 1000);
        List<String> messages = new ArrayList<>();
        monitor.setReplySender((channelId, messageId, content) -> messages.add(content));

        monitor.tick();
        monitor.tick();

        long feedback = messages.stream().filter(m -> m.contains("Same status update text")).count();
        assertEquals(1, feedback);
        monitor.close();
    }

    @Test
    void tickSendsToThreadWhenDeliveryChannelIdSet() {
        CursorCloudAdapter adapter = new CursorCloudAdapter() {
            @Override
            public CursorAgentLaunchResult launchAgent(CursorAgentLaunchRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CursorAgentDetails getAgent(String agentId) {
                return new CursorAgentDetails(
                        agentId, "Run", "RUNNING", "https://github.com/acme/repo",
                        "main", "br", "https://cursor.com/agents?id=" + agentId, null, null, Instant.now());
            }

            @Override
            public CursorAgentConversation getConversation(String agentId) {
                return new CursorAgentConversation(agentId, List.of());
            }

            @Override
            public void addFollowup(String agentId, String promptText) {
                throw new UnsupportedOperationException();
            }
        };
        StateStore store = new StateStore();
        LifecycleRunRecord runState = new LifecycleRunRecord(
                "bc_thread", "session", "acme/repo", "https://github.com/acme/repo",
                "main", "br", "https://cursor.com/agents?id=bc_thread",
                "Request", "chan-parent", "msg-1", "thread-123", Instant.now(), "CREATING");
        store.put("cursor:run:bc_thread", runState);
        CursorCloudRunMonitor monitor = new CursorCloudRunMonitor(adapter, store, 1000);
        final String[] sentTarget = new String[1];
        final String[] sentMessageId = new String[1];
        monitor.setReplySender((channelId, messageId, content) -> {
            sentTarget[0] = channelId;
            sentMessageId[0] = messageId;
        });

        monitor.tick();

        assertEquals("thread-123", sentTarget[0]);
        assertEquals(null, sentMessageId[0]);
        monitor.close();
    }

    @Test
    void tickSendsToChannelWithReplyToMessageIdWhenDeliveryChannelIdNullOrThreadCreateFailed() {
        CursorCloudAdapter adapter = new CursorCloudAdapter() {
            @Override
            public CursorAgentLaunchResult launchAgent(CursorAgentLaunchRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CursorAgentDetails getAgent(String agentId) {
                return new CursorAgentDetails(
                        agentId, "Run", "RUNNING", "https://github.com/acme/repo",
                        "main", "br", "https://cursor.com/agents?id=" + agentId, null, null, Instant.now());
            }

            @Override
            public CursorAgentConversation getConversation(String agentId) {
                return new CursorAgentConversation(agentId, List.of());
            }

            @Override
            public void addFollowup(String agentId, String promptText) {
                throw new UnsupportedOperationException();
            }
        };
        StateStore store = new StateStore();
        LifecycleRunRecord runState = new LifecycleRunRecord(
                "bc_ch", "session", "acme/repo", "https://github.com/acme/repo",
                "main", "br", "https://cursor.com/agents?id=bc_ch",
                "Request", "chan-1", "msg-reply", null, Instant.now(), "CREATING");
        store.put("cursor:run:bc_ch", runState);
        CursorCloudRunMonitor monitor = new CursorCloudRunMonitor(adapter, store, 1000);
        final String[] sentTarget = new String[1];
        final String[] sentMessageId = new String[1];
        monitor.setReplySender((channelId, messageId, content) -> {
            sentTarget[0] = channelId;
            sentMessageId[0] = messageId;
        });

        monitor.tick();

        assertEquals("chan-1", sentTarget[0]);
        assertEquals("msg-reply", sentMessageId[0]);
        monitor.close();
    }
}
