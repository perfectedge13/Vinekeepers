package com.vinekeepers.state;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void getByDeliveryTargetIdResolvesByChannelId() {
        LifecycleContextStore store = new LifecycleContextStore();
        LifecycleContext ctx = new LifecycleContext("ctx-1", "chan-1", NOW, null, null, null, null, null, null, null);
        store.put(ctx);
        assertTrue(store.getByDeliveryTargetId("chan-1").isPresent());
        assertEquals(ctx, store.getByDeliveryTargetId("chan-1").orElseThrow());
    }

    @Test
    void getByDeliveryTargetIdResolvesByDeliveryChannelIdWhenSet() {
        LifecycleContextStore store = new LifecycleContextStore();
        LifecycleContext ctx = new LifecycleContext("ctx-1", "chan-1", NOW, null, null, null, null, null, null, "thread-123");
        store.put(ctx);
        assertTrue(store.getByDeliveryTargetId("thread-123").isPresent());
        assertEquals(ctx, store.getByDeliveryTargetId("thread-123").orElseThrow());
        assertTrue(store.getByDeliveryTargetId("chan-1").isPresent());
    }

    @Test
    void getByDeliveryTargetIdDoesNotIndexThreadCreateFailed() {
        LifecycleContextStore store = new LifecycleContextStore();
        LifecycleContext ctx = new LifecycleContext("ctx-1", "chan-1", NOW, null, null, null, null, null, null, "THREAD_CREATE_FAILED");
        store.put(ctx);
        assertTrue(store.getByDeliveryTargetId("THREAD_CREATE_FAILED").isEmpty());
        assertTrue(store.getByChannelId("chan-1").isPresent());
    }

    @Test
    void rePutWithDifferentDeliveryChannelId_oldThreadNoLongerResolves() {
        LifecycleContextStore store = new LifecycleContextStore();
        LifecycleContext ctx = new LifecycleContext("ctx-1", "chan-1", NOW, null, null, null, null, null, null, "thread-old");
        store.put(ctx);
        assertTrue(store.getByDeliveryTargetId("thread-old").isPresent());
        LifecycleContext updated = new LifecycleContext("ctx-1", "chan-1", NOW, null, null, null, null, null, null, "thread-new");
        store.put(updated);
        assertTrue(store.getByDeliveryTargetId("thread-old").isEmpty());
        assertTrue(store.getByDeliveryTargetId("thread-new").isPresent());
        assertEquals(updated, store.getByDeliveryTargetId("thread-new").orElseThrow());
    }

    @Test
    void rePutWithNullDeliveryChannelId_removesOldDeliveryTargetFromIndex() {
        LifecycleContextStore store = new LifecycleContextStore();
        LifecycleContext ctx = new LifecycleContext("ctx-1", "chan-1", NOW, null, null, null, null, null, null, "thread-1");
        store.put(ctx);
        assertTrue(store.getByDeliveryTargetId("thread-1").isPresent());
        LifecycleContext updated = new LifecycleContext("ctx-1", "chan-1", NOW, null, null, null, null, null, null, null);
        store.put(updated);
        assertTrue(store.getByDeliveryTargetId("thread-1").isEmpty());
        assertTrue(store.getByChannelId("chan-1").isPresent());
    }

    @Test
    void getByDeliveryTargetIdWithBlankReturnsEmpty() {
        LifecycleContextStore store = new LifecycleContextStore();
        assertTrue(store.getByDeliveryTargetId(null).isEmpty());
        assertTrue(store.getByDeliveryTargetId("").isEmpty());
        assertTrue(store.getByDeliveryTargetId("   ").isEmpty());
    }

    @Test
    void setDeliveryTargetIdIgnoresNullBlankAndSentinel() {
        LifecycleContextStore store = new LifecycleContextStore();
        LifecycleContext ctx = new LifecycleContext("ctx-1", "chan-1", NOW, null, null, null, null, null, null, null);
        store.put(ctx);
        store.setDeliveryTargetId("ctx-1", null);
        store.setDeliveryTargetId("ctx-1", "");
        store.setDeliveryTargetId("ctx-1", "THREAD_CREATE_FAILED");
        assertTrue(store.getByDeliveryTargetId("thread-1").isEmpty());
        assertTrue(store.getByContextId("ctx-1").orElseThrow().getDeliveryChannelId() == null);
    }

    @Test
    void setDeliveryTargetIdUpdatesContextAndIndex() {
        LifecycleContextStore store = new LifecycleContextStore();
        LifecycleContext ctx = new LifecycleContext("ctx-1", "chan-1", NOW, null, "arrietty", null, null, null, null, null);
        store.put(ctx);
        assertTrue(store.getByDeliveryTargetId("thread-xyz").isEmpty());
        store.setDeliveryTargetId("ctx-1", "thread-xyz");
        assertTrue(store.getByDeliveryTargetId("thread-xyz").isPresent());
        assertEquals(ctx.getContextId(), store.getByDeliveryTargetId("thread-xyz").orElseThrow().getContextId());
        assertTrue(store.getByChannelId("chan-1").isPresent());
        assertEquals("thread-xyz", store.getByContextId("ctx-1").orElseThrow().getDeliveryChannelId());
    }
}
