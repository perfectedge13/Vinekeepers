package com.vinekeepers.state;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleContextStoreTest {

    private static final Instant NOW = Instant.now();

    @Test
    void putAndGetByContextId() {
        LifecycleContextStore store = new LifecycleContextStore();
        LifecycleContext ctx = new LifecycleContext("ctx-1", "chan-1", NOW);
        store.put(ctx);
        assertTrue(store.getByContextId("ctx-1").isPresent());
        assertEquals(ctx, store.getByContextId("ctx-1").orElseThrow());
        assertTrue(store.getByContextId("missing").isEmpty());
    }

    @Test
    void putAndGetByChannelId() {
        LifecycleContextStore store = new LifecycleContextStore();
        LifecycleContext ctx = new LifecycleContext("ctx-1", "chan-1", NOW);
        store.put(ctx);
        assertTrue(store.getByChannelId("chan-1").isPresent());
        assertEquals(ctx, store.getByChannelId("chan-1").orElseThrow());
        assertTrue(store.getByChannelId("other").isEmpty());
    }

    @Test
    void putAndGetByExternalRunId() {
        LifecycleContextStore store = new LifecycleContextStore();
        LifecycleContext ctx = new LifecycleContext("ctx-1", "chan-1", NOW, "run-123");
        store.put(ctx);
        assertTrue(store.getByExternalRunId("run-123").isPresent());
        assertEquals(ctx, store.getByExternalRunId("run-123").orElseThrow());
        assertTrue(store.getByExternalRunId("other").isEmpty());
    }

    @Test
    void bindExternalRunIdUpdatesContextAndIndex() {
        LifecycleContextStore store = new LifecycleContextStore();
        LifecycleContext ctx = new LifecycleContext("ctx-1", "chan-1", NOW);
        store.put(ctx);
        assertTrue(store.getByExternalRunId("agent-1").isEmpty());
        store.bindExternalRunId("ctx-1", "agent-1");
        assertEquals("agent-1", ctx.getExternalRunId());
        assertTrue(store.getByExternalRunId("agent-1").isPresent());
        assertEquals(ctx, store.getByExternalRunId("agent-1").orElseThrow());
    }

    @Test
    void bindExternalRunIdWithNullContextIdIsNoOp() {
        LifecycleContextStore store = new LifecycleContextStore();
        store.put(new LifecycleContext("ctx-1", "chan-1", NOW));
        store.bindExternalRunId(null, "agent-1");
        store.bindExternalRunId("missing", "agent-2");
        assertTrue(store.getByExternalRunId("agent-1").isEmpty());
        assertTrue(store.getByExternalRunId("agent-2").isEmpty());
    }

    @Test
    void contextIdsReturnsAllIds() {
        LifecycleContextStore store = new LifecycleContextStore();
        store.put(new LifecycleContext("ctx-a", "chan-a", NOW));
        store.put(new LifecycleContext("ctx-b", "chan-b", NOW));
        Set<String> ids = store.contextIds();
        assertEquals(2, ids.size());
        assertTrue(ids.contains("ctx-a"));
        assertTrue(ids.contains("ctx-b"));
    }

    @Test
    void putNullIsNoOp() {
        LifecycleContextStore store = new LifecycleContextStore();
        store.put(null);
        assertTrue(store.contextIds().isEmpty());
    }

    @Test
    void getByChannelIdWithBlankReturnsEmpty() {
        LifecycleContextStore store = new LifecycleContextStore();
        assertTrue(store.getByChannelId(null).isEmpty());
        assertTrue(store.getByChannelId("").isEmpty());
        assertTrue(store.getByChannelId("   ").isEmpty());
    }
}
