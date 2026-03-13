package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.DiscordGateway;
import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.LifecycleContext;
import com.vinekeepers.state.LifecycleContextStore;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateThreadActionTest {

    @Test
    void runReturnsThreadCreateFailedWhenRouterNull() {
        CreateThreadAction action = new CreateThreadAction(null, new LifecycleContextStore());
        Object result = action.run(null, Map.of("channelId", "chan-1"), Map.of());
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, result);
    }

    @Test
    void runReturnsThreadCreateFailedWhenGatewayNull() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        // default gateway not set
        CreateThreadAction action = new CreateThreadAction(router, store);
        Object result = action.run(null, Map.of("channelId", "chan-1"), Map.of());
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, result);
    }

    @Test
    void runReturnsThreadCreateFailedWhenGatewayNotConnected() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(new StubGateway(false, null));
        CreateThreadAction action = new CreateThreadAction(router, store);
        Object result = action.run(null, Map.of("channelId", "chan-1"), Map.of());
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, result);
    }

    @Test
    void runReturnsThreadCreateFailedWhenChannelIdMissing() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(new StubGateway(true, "thread-1"));
        CreateThreadAction action = new CreateThreadAction(router, store);
        Object result = action.run(null, Map.of(), Map.of());
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, result);
    }

    @Test
    void runReturnsThreadCreateFailedWhenChannelIdBlank() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(new StubGateway(true, "thread-1"));
        CreateThreadAction action = new CreateThreadAction(router, store);
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, action.run(null, Map.of("channelId", ""), Map.of()));
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, action.run(null, Map.of("channelId", "   "), Map.of()));
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, action.run(null, Map.of(), Map.of("channelId", "")));
    }

    @Test
    void runUsesDefaultThreadNameWhenThreadNameBlank() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        StubGateway gateway = new StubGateway(true, "thread-456");
        router.setDefaultGateway(gateway);
        CreateThreadAction action = new CreateThreadAction(router, store);
        action.run(null, Map.of("channelId", "chan-1"), Map.of());
        assertEquals("chan-1", gateway.lastParentChannelId);
        assertEquals("Room updates", gateway.lastThreadName);
    }

    @Test
    void runReturnsThreadIdWhenGatewayCreatesThread() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(new StubGateway(true, "thread-789"));
        CreateThreadAction action = new CreateThreadAction(router, store);
        Object result = action.run(null,
                Map.of("channelId", "chan-1", "threadName", "My thread"),
                Map.of());
        assertEquals("thread-789", result);
    }

    @Test
    void runReturnsThreadCreateFailedWhenGatewayReturnsNull() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(new StubGateway(true, null));
        CreateThreadAction action = new CreateThreadAction(router, store);
        Object result = action.run(null, Map.of("channelId", "chan-1"), Map.of());
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, result);
    }

    @Test
    void runPrefersBindChannelIdAndThreadNameOverState() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        StubGateway gateway = new StubGateway(true, "t-1");
        router.setDefaultGateway(gateway);
        CreateThreadAction action = new CreateThreadAction(router, store);
        action.run(null,
                Map.of("channelId", "chan-state", "threadName", "State thread"),
                Map.of("channelId", "chan-bind", "threadName", "Bind thread"));
        assertEquals("chan-bind", gateway.lastParentChannelId);
        assertEquals("Bind thread", gateway.lastThreadName);
    }

    @Test
    void runUsesLifecycleOwnerGatewayWhenChannelHasContext() {
        LifecycleContextStore store = new LifecycleContextStore();
        LifecycleContext ctx = new LifecycleContext("ctx-1", "chan-lifecycle", java.time.Instant.now(),
                null, "arrietty", null, null, null, null, null);
        store.put(ctx);
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        StubGateway defaultGw = new StubGateway(true, "thread-default");
        StubGateway arriettyGw = new StubGateway(true, "thread-arrietty");
        router.setDefaultGateway(defaultGw);
        router.registerSender("arrietty", null, arriettyGw);
        CreateThreadAction action = new CreateThreadAction(router, store);
        Object result = action.run(null, Map.of("channelId", "chan-lifecycle", "threadName", "Room updates"), Map.of());
        assertEquals("thread-arrietty", result);
        assertEquals("chan-lifecycle", arriettyGw.lastParentChannelId);
        assertEquals("Room updates", arriettyGw.lastThreadName);
    }

    @Test
    void runUsesDefaultGatewayWhenChannelHasNoContext() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        StubGateway defaultGw = new StubGateway(true, "thread-1");
        router.setDefaultGateway(defaultGw);
        CreateThreadAction action = new CreateThreadAction(router, store);
        action.run(null, Map.of("channelId", "chan-any"), Map.of());
        assertEquals("chan-any", defaultGw.lastParentChannelId);
    }

    @Test
    void runUpdatesStoreWithThreadIdWhenContextIdInState() {
        LifecycleContextStore store = new LifecycleContextStore();
        LifecycleContext ctx = new LifecycleContext("ctx-1", "chan-1", java.time.Instant.now(),
                null, "arrietty", null, null, null, null, null);
        store.put(ctx);
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(new StubGateway(true, "thread-123"));
        CreateThreadAction action = new CreateThreadAction(router, store);
        action.run(null, Map.of("channelId", "chan-1", "contextId", "ctx-1"), Map.of());
        assertTrue(store.getByDeliveryTargetId("thread-123").isPresent());
        assertEquals(ctx.getContextId(), store.getByDeliveryTargetId("thread-123").orElseThrow().getContextId());
    }

    @Test
    void runDoesNotCallSetDeliveryTargetIdWhenContextIdAbsent() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(new StubGateway(true, "thread-456"));
        CreateThreadAction action = new CreateThreadAction(router, store);
        action.run(null, Map.of("channelId", "chan-1"), Map.of());
        assertTrue(store.getByDeliveryTargetId("thread-456").isEmpty());
    }

    @Test
    void runDoesNotCallSetDeliveryTargetIdWhenThreadCreationFails() {
        LifecycleContextStore store = new LifecycleContextStore();
        LifecycleContext ctx = new LifecycleContext("ctx-1", "chan-1", java.time.Instant.now(),
                null, "arrietty", null, null, null, null, null);
        store.put(ctx);
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(new StubGateway(true, null));
        CreateThreadAction action = new CreateThreadAction(router, store);
        Object result = action.run(null, Map.of("channelId", "chan-1", "contextId", "ctx-1"), Map.of());
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, result);
        assertTrue(store.getByContextId("ctx-1").orElseThrow().getDeliveryChannelId() == null);
    }

    private static final class StubGateway implements DiscordGateway {
        private final boolean connected;
        private final String threadIdToReturn;
        String lastParentChannelId;
        String lastThreadName;

        StubGateway(boolean connected, String threadIdToReturn) {
            this.connected = connected;
            this.threadIdToReturn = threadIdToReturn;
        }

        @Override
        public void connect(java.util.function.Consumer<Event> publisher) {}

        @Override
        public void shutdown() {}

        @Override
        public void send(String channelId, String messageId, String content) {}

        @Override
        public void send(String channelId, String messageId, String content, java.util.List<java.util.List<java.util.Map<String, Object>>> components) {}

        @Override
        public void sendFollowUp(String token, String content) {}

        @Override
        public void sendFollowUp(String token, String content, java.util.List<java.util.List<java.util.Map<String, Object>>> components) {}

        @Override
        public void updateMessage(String token, String content) {}

        @Override
        public void updateMessage(String token, String content, java.util.List<java.util.List<java.util.Map<String, Object>>> components) {}

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public String createThreadChannel(String parentChannelId, String threadName) {
            this.lastParentChannelId = parentChannelId;
            this.lastThreadName = threadName;
            return threadIdToReturn;
        }
    }
}
