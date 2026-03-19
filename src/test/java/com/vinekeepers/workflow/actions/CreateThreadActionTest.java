package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.CreateThreadRequest;
import com.vinekeepers.connectors.DiscordGateway;
import com.vinekeepers.connectors.DiscordSpaceOperations;
import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.connectors.SpaceOperations;
import com.vinekeepers.connectors.SpaceOperationsRegistry;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.LifecycleContext;
import com.vinekeepers.state.LifecycleContextStore;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateThreadActionTest {

    private static SpaceOperationsRegistry registryWithDiscord(OutboundDeliveryRouter router, LifecycleContextStore store) {
        SpaceOperationsRegistry registry = new SpaceOperationsRegistry();
        registry.register("discord", new DiscordSpaceOperations(router, store));
        return registry;
    }

    @Test
    void runReturnsThreadCreateFailedWhenRouterNull() {
        SpaceOperationsRegistry registry = new SpaceOperationsRegistry();
        CreateThreadAction action = new CreateThreadAction(registry);
        Object result = action.run(new Event("discord:ch", "m", Map.of()), Map.of("channelId", "chan-1"), Map.of());
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, result);
    }

    @Test
    void runReturnsThreadCreateFailedWhenGatewayNull() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        CreateThreadAction action = new CreateThreadAction(registryWithDiscord(router, store));
        Object result = action.run(new Event("discord:ch", "m", Map.of()), Map.of("channelId", "chan-1"), Map.of());
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, result);
    }

    @Test
    void runReturnsThreadCreateFailedWhenGatewayNotConnected() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(new StubGateway(false, null));
        CreateThreadAction action = new CreateThreadAction(registryWithDiscord(router, store));
        Object result = action.run(new Event("discord:ch", "m", Map.of()), Map.of("channelId", "chan-1"), Map.of());
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, result);
    }

    @Test
    void runReturnsThreadCreateFailedWhenChannelIdMissing() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(new StubGateway(true, "thread-1"));
        CreateThreadAction action = new CreateThreadAction(registryWithDiscord(router, store));
        Object result = action.run(new Event("discord:ch", "m", Map.of()), Map.of(), Map.of());
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, result);
    }

    @Test
    void runReturnsThreadCreateFailedWhenChannelIdBlank() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(new StubGateway(true, "thread-1"));
        CreateThreadAction action = new CreateThreadAction(registryWithDiscord(router, store));
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, action.run(new Event("discord:ch", "m", Map.of()), Map.of("channelId", ""), Map.of()));
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, action.run(new Event("discord:ch", "m", Map.of()), Map.of("channelId", "   "), Map.of()));
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, action.run(new Event("discord:ch", "m", Map.of()), Map.of(), Map.of("channelId", "")));
    }

    @Test
    void runUsesDefaultThreadNameWhenThreadNameBlank() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        StubGateway gateway = new StubGateway(true, "thread-456");
        router.setDefaultGateway(gateway);
        CreateThreadAction action = new CreateThreadAction(registryWithDiscord(router, store));
        action.run(new Event("discord:ch", "m", Map.of()), Map.of("channelId", "chan-1"), Map.of());
        assertEquals("chan-1", gateway.lastParentChannelId);
        assertEquals("Room updates", gateway.lastThreadName);
    }

    @Test
    void runReturnsThreadIdWhenGatewayCreatesThread() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(new StubGateway(true, "thread-789"));
        CreateThreadAction action = new CreateThreadAction(registryWithDiscord(router, store));
        Object result = action.run(new Event("discord:ch", "m", Map.of()),
                Map.of("channelId", "chan-1", "threadName", "My thread"),
                Map.of());
        assertEquals("thread-789", result);
    }

    @Test
    void runReturnsThreadCreateFailedWhenGatewayReturnsNull() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(new StubGateway(true, null));
        CreateThreadAction action = new CreateThreadAction(registryWithDiscord(router, store));
        Object result = action.run(new Event("discord:ch", "m", Map.of()), Map.of("channelId", "chan-1"), Map.of());
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, result);
    }

    @Test
    void runPrefersBindChannelIdAndThreadNameOverState() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        StubGateway gateway = new StubGateway(true, "t-1");
        router.setDefaultGateway(gateway);
        CreateThreadAction action = new CreateThreadAction(registryWithDiscord(router, store));
        action.run(new Event("discord:ch", "m", Map.of()),
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
        CreateThreadAction action = new CreateThreadAction(registryWithDiscord(router, store));
        Object result = action.run(new Event("discord:ch", "m", Map.of()), Map.of("channelId", "chan-lifecycle", "threadName", "Room updates"), Map.of());
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
        CreateThreadAction action = new CreateThreadAction(registryWithDiscord(router, store));
        action.run(new Event("discord:ch", "m", Map.of()), Map.of("channelId", "chan-any"), Map.of());
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
        CreateThreadAction action = new CreateThreadAction(registryWithDiscord(router, store));
        action.run(new Event("discord:ch", "m", Map.of()), Map.of("channelId", "chan-1", "contextId", "ctx-1"), Map.of());
        assertTrue(store.getByDeliveryTargetId("thread-123").isPresent());
        assertEquals(ctx.getContextId(), store.getByDeliveryTargetId("thread-123").orElseThrow().getContextId());
    }

    @Test
    void runDoesNotCallSetDeliveryTargetIdWhenContextIdAbsent() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(new StubGateway(true, "thread-456"));
        CreateThreadAction action = new CreateThreadAction(registryWithDiscord(router, store));
        action.run(new Event("discord:ch", "m", Map.of()), Map.of("channelId", "chan-1"), Map.of());
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
        CreateThreadAction action = new CreateThreadAction(registryWithDiscord(router, store));
        Object result = action.run(new Event("discord:ch", "m", Map.of()), Map.of("channelId", "chan-1", "contextId", "ctx-1"), Map.of());
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, result);
        assertTrue(store.getByContextId("ctx-1").orElseThrow().getDeliveryChannelId() == null);
    }

    @Test
    void runReturnsThreadCreateFailedWhenUnknownSourcePrefix() {
        SpaceOperationsRegistry registry = new SpaceOperationsRegistry();
        CreateThreadAction action = new CreateThreadAction(registry);
        Object result = action.run(new Event("unknown:xyz", "m", Map.of()), Map.of("channelId", "chan-1"), Map.of());
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, result);
    }

    @Test
    void whenEventIsNull_returnsThreadCreateFailed() {
        final boolean[] createThreadCalled = { false };
        SpaceOperations stub = new SpaceOperations() {
            @Override
            public com.vinekeepers.connectors.CreateRoomResult createRoom(com.vinekeepers.connectors.CreateRoomRequest request) {
                return new com.vinekeepers.connectors.CreateRoomResult.Failure(com.vinekeepers.connectors.CreateRoomFailureReason.GUILD_ID_MISSING);
            }
            @Override
            public com.vinekeepers.connectors.CreateThreadResult createThread(CreateThreadRequest request) {
                createThreadCalled[0] = true;
                return new com.vinekeepers.connectors.CreateThreadResult.Success("stub-thread");
            }
        };
        SpaceOperationsRegistry registry = new SpaceOperationsRegistry();
        registry.register("discord", stub);
        CreateThreadAction action = new CreateThreadAction(registry);
        Object result = action.run(null, Map.of("channelId", "chan-1"), Map.of());
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, result);
        assertFalse(createThreadCalled[0], "capability should not be invoked when event is null");
    }

    @Test
    void whenSourcePrefixUnknown_returnsThreadCreateFailed() {
        SpaceOperationsRegistry registry = new SpaceOperationsRegistry();
        CreateThreadAction action = new CreateThreadAction(registry);
        Object result = action.run(new Event("other:x", "m", Map.of()), Map.of("channelId", "chan-1"), Map.of());
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, result);
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
