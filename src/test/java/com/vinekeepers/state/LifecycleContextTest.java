package com.vinekeepers.state;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LifecycleContextTest {

    private static final Instant NOW = Instant.parse("2026-03-09T12:00:00Z");

    @Test
    void constructorWithThreeArgs() {
        LifecycleContext ctx = new LifecycleContext("ctx-1", "chan-1", NOW);
        assertEquals("ctx-1", ctx.getContextId());
        assertEquals("chan-1", ctx.getChannelId());
        assertEquals(NOW, ctx.getCreatedAt());
        assertNull(ctx.getExternalRunId());
    }

    @Test
    void constructorWithFourArgs() {
        LifecycleContext ctx = new LifecycleContext("ctx-2", "chan-2", NOW, "run-123");
        assertEquals("ctx-2", ctx.getContextId());
        assertEquals("chan-2", ctx.getChannelId());
        assertEquals("run-123", ctx.getExternalRunId());
    }

    @Test
    void constructorWithNullCreatedAtUsesNow() {
        LifecycleContext ctx = new LifecycleContext("ctx-3", "chan-3", null);
        assertNotNull(ctx.getCreatedAt());
    }

    @Test
    void setExternalRunId() {
        LifecycleContext ctx = new LifecycleContext("ctx-4", "chan-4", NOW);
        ctx.setExternalRunId("agent-456");
        assertEquals("agent-456", ctx.getExternalRunId());
    }

    @Test
    void constructorRejectsNullContextId() {
        assertThrows(NullPointerException.class,
                () -> new LifecycleContext(null, "chan", NOW));
    }

    @Test
    void constructorRejectsNullChannelId() {
        assertThrows(NullPointerException.class,
                () -> new LifecycleContext("ctx", null, NOW));
    }

    @Test
    void constructorWithEightArgsSetsNewFieldsAndDefaultStatusActive() {
        LifecycleContext ctx = new LifecycleContext(
                "ctx-5", "chan-5", NOW,
                "run-99", "luna", "luna-a1b2c3d4",
                "https://github.com/owner/repo", "Add tests");
        assertEquals("ctx-5", ctx.getContextId());
        assertEquals("chan-5", ctx.getChannelId());
        assertEquals("run-99", ctx.getExternalRunId());
        assertEquals("luna", ctx.getConfiguredBotId());
        assertEquals("luna-a1b2c3d4", ctx.getRuntimeBotInstanceId());
        assertEquals("https://github.com/owner/repo", ctx.getRepo());
        assertEquals("Add tests", ctx.getRequestText());
        assertEquals("active", ctx.getStatus());
    }

    @Test
    void constructorWithEightArgsAllowsNullOptionalFields() {
        LifecycleContext ctx = new LifecycleContext(
                "ctx-6", "chan-6", NOW,
                null, null, null, null, null);
        assertNull(ctx.getExternalRunId());
        assertNull(ctx.getConfiguredBotId());
        assertNull(ctx.getRuntimeBotInstanceId());
        assertNull(ctx.getRepo());
        assertNull(ctx.getRequestText());
        assertEquals("active", ctx.getStatus());
    }

    @Test
    void constructorWithNineArgsSetsStatus() {
        LifecycleContext ctx = new LifecycleContext(
                "ctx-7", "chan-7", NOW,
                null, "arrietty", "arrietty-x", "repo", "req", "provisioning");
        assertEquals("provisioning", ctx.getStatus());
    }

    @Test
    void setStatus() {
        LifecycleContext ctx = new LifecycleContext("ctx-8", "chan-8", NOW);
        assertEquals("active", ctx.getStatus());
        ctx.setStatus("active");
        assertEquals("active", ctx.getStatus());
        ctx.setStatus("failed");
        assertEquals("failed", ctx.getStatus());
    }
}
