package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.DiscordGateway;
import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.LifecycleContextStore;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CreateThreadActionTest {

    @Test
    void runReturnsThreadCreateFailedWhenRouterNull() {
        CreateThreadAction action = new CreateThreadAction(null);
        Object result = action.run(null, Map.of("channelId", "chan-1"), Map.of());
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, result);
    }

    @Test
    void runReturnsThreadCreateFailedWhenGatewayNull() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        // default gateway not set
        CreateThreadAction action = new CreateThreadAction(router);
        Object result = action.run(null, Map.of("channelId", "chan-1"), Map.of());
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, result);
    }

    @Test
    void runReturnsThreadCreateFailedWhenGatewayNotConnected() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(new StubGateway(false, null));
        CreateThreadAction action = new CreateThreadAction(router);
        Object result = action.run(null, Map.of("channelId", "chan-1"), Map.of());
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, result);
    }

    @Test
    void runReturnsThreadCreateFailedWhenChannelIdMissing() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(new StubGateway(true, "thread-1"));
        CreateThreadAction action = new CreateThreadAction(router);
        Object result = action.run(null, Map.of(), Map.of());
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, result);
    }

    @Test
    void runUsesDefaultThreadNameWhenThreadNameBlank() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        StubGateway gateway = new StubGateway(true, "thread-456");
        router.setDefaultGateway(gateway);
        CreateThreadAction action = new CreateThreadAction(router);
        action.run(null, Map.of("channelId", "chan-1"), Map.of());
        assertEquals("chan-1", gateway.lastParentChannelId);
        assertEquals("Room updates", gateway.lastThreadName);
    }

    @Test
    void runReturnsThreadIdWhenGatewayCreatesThread() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        router.setDefaultGateway(new StubGateway(true, "thread-789"));
        CreateThreadAction action = new CreateThreadAction(router);
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
        CreateThreadAction action = new CreateThreadAction(router);
        Object result = action.run(null, Map.of("channelId", "chan-1"), Map.of());
        assertEquals(CreateThreadAction.THREAD_CREATE_FAILED, result);
    }

    @Test
    void runPrefersBindChannelIdAndThreadNameOverState() {
        LifecycleContextStore store = new LifecycleContextStore();
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(store);
        StubGateway gateway = new StubGateway(true, "t-1");
        router.setDefaultGateway(gateway);
        CreateThreadAction action = new CreateThreadAction(router);
        action.run(null,
                Map.of("channelId", "chan-state", "threadName", "State thread"),
                Map.of("channelId", "chan-bind", "threadName", "Bind thread"));
        assertEquals("chan-bind", gateway.lastParentChannelId);
        assertEquals("Bind thread", gateway.lastThreadName);
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
