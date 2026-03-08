package com.vinekeepers.core.cursor;

import com.vinekeepers.connectors.DiscordReplySender;
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
        LunaCloudRunState runState = new LunaCloudRunState(
                "bc_1", "session", "acme/vinekeepers", "https://github.com/acme/vinekeepers",
                "main", "luna/add-tests", "https://cursor.com/agents?id=bc_1",
                "Ship it", "chan-1", "msg-1", Instant.now(), "CREATING");
        store.put("cursor:run:bc_1", runState);
        CursorCloudRunMonitor monitor = new CursorCloudRunMonitor(adapter, store, 1000);
        List<String> messages = new ArrayList<>();
        DiscordReplySender sender = (channelId, messageId, content) -> messages.add(content);
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
        LunaCloudRunState runState = new LunaCloudRunState(
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
        assertTrue(messages.getLast().contains("pull/12"));
        monitor.close();
    }
}
