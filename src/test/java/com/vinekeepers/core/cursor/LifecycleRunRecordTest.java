package com.vinekeepers.core.cursor;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleRunRecordTest {

    private static final Instant NOW = Instant.parse("2026-03-09T12:00:00Z");

    @Test
    void constructorAndGetters() {
        LifecycleRunRecord record = new LifecycleRunRecord(
                "agent-1", "session-1", "acme/repo", "https://github.com/acme/repo",
                "main", "luna/feature", "https://cursor.com/agents?id=agent-1",
                "Add tests", "chan-1", "msg-1", NOW, "CREATING");
        assertEquals("agent-1", record.getAgentId());
        assertEquals("session-1", record.getSessionKey());
        assertEquals("acme/repo", record.getProjectInput());
        assertEquals("https://github.com/acme/repo", record.getRepositoryUrl());
        assertEquals("main", record.getBaseRef());
        assertEquals("luna/feature", record.getBranchName());
        assertEquals("chan-1", record.getChannelId());
        assertEquals("msg-1", record.getReplyToMessageId());
        assertEquals(NOW, record.getLaunchedAt());
        assertEquals("CREATING", record.getStatus());
        assertNull(record.getPrUrl());
        assertNull(record.getSummary());
        assertFalse(record.isTerminal());
        assertFalse(record.isTerminalNotificationSent());
    }

    @Test
    void isTerminalStatus() {
        assertTrue(LifecycleRunRecord.isTerminalStatus("FINISHED"));
        assertTrue(LifecycleRunRecord.isTerminalStatus("ERROR"));
        assertTrue(LifecycleRunRecord.isTerminalStatus("EXPIRED"));
        assertTrue(LifecycleRunRecord.isTerminalStatus("finished"));
        assertFalse(LifecycleRunRecord.isTerminalStatus("RUNNING"));
        assertFalse(LifecycleRunRecord.isTerminalStatus("CREATING"));
        assertFalse(LifecycleRunRecord.isTerminalStatus(null));
    }

    @Test
    void applyAgentDetailsUpdatesStatusAndPrUrl() {
        LifecycleRunRecord record = new LifecycleRunRecord(
                "a", "s", "p", "url", "main", "br", "agentUrl", "req",
                "ch", "msg", NOW, "CREATING");
        CursorAgentDetails details = new CursorAgentDetails(
                "a", "Run", "RUNNING", "url", "main", "br", "agentUrl",
                null, null, NOW);
        record.applyAgentDetails(details);
        assertEquals("RUNNING", record.getStatus());
        assertNull(record.getPrUrl());
        assertFalse(record.isTerminal());

        record.applyAgentDetails(new CursorAgentDetails(
                "a", "Run", "FINISHED", "url", "main", "br", "agentUrl",
                "https://github.com/acme/repo/pull/1", "Done", NOW));
        assertEquals("FINISHED", record.getStatus());
        assertEquals("https://github.com/acme/repo/pull/1", record.getPrUrl());
        assertEquals("Done", record.getSummary());
        assertTrue(record.isTerminal());
        assertTrue(record.getCompletedAt() != null);
    }

    @Test
    void applyAgentDetailsNullIsNoOp() {
        LifecycleRunRecord record = new LifecycleRunRecord(
                "a", "s", "p", "url", "main", "br", "agentUrl", "req", "ch", "msg", NOW, "CREATING");
        record.applyAgentDetails(null);
        assertEquals("CREATING", record.getStatus());
    }

    @Test
    void recordAssistantMessage() {
        LifecycleRunRecord record = new LifecycleRunRecord(
                "a", "s", "p", "url", "main", "br", "agentUrl", "req", "ch", "msg", NOW, "RUNNING");
        CursorAgentMessage message = new CursorAgentMessage("mid", "assistant_message", "Working on it");
        record.recordAssistantMessage(message);
        assertEquals("mid", record.getLastAssistantMessageId());
        assertEquals("Working on it", record.getLastAssistantMessage());
        record.recordAssistantMessage(null);
        assertEquals("mid", record.getLastAssistantMessageId());
    }

    @Test
    void markTerminalNotificationSent() {
        LifecycleRunRecord record = new LifecycleRunRecord(
                "a", "s", "p", "url", "main", "br", "agentUrl", "req", "ch", "msg", NOW, "FINISHED");
        assertFalse(record.isTerminalNotificationSent());
        record.markTerminalNotificationSent();
        assertTrue(record.isTerminalNotificationSent());
    }
}
