package com.vinekeepers.connectors;

import com.vinekeepers.state.LifecycleContext;
import com.vinekeepers.state.LifecycleContextStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OutboundDeliveryRouter: lifecycle context → configuredBotId sender resolution,
 * default sender when no context, and no fallback when lifecycle channel has no sender for that bot.
 */
class OutboundDeliveryRouterTest {

    private static final Instant NOW = Instant.now();

    private LifecycleContextStore lifecycleContextStore;
    private OutboundDeliveryRouter router;
    private RecordingSender defaultSender;
    private RecordingSender lunaSender;

    @BeforeEach
    void setUp() {
        lifecycleContextStore = new LifecycleContextStore();
        router = new OutboundDeliveryRouter(lifecycleContextStore);
        defaultSender = new RecordingSender();
        lunaSender = new RecordingSender();
        router.setDefaultSender(defaultSender);
    }

    @Test
    void send_whenChannelHasLifecycleContextWithConfiguredBotId_usesThatBotsSender() {
        lifecycleContextStore.put(new LifecycleContext("ctx-1", "lifecycle-ch", NOW, null, "luna", null, null, null));
        router.registerSender("luna", lunaSender, null);

        router.send("lifecycle-ch", "msg-1", "Hello from Luna");

        assertEquals(1, lunaSender.sendCalls.get());
        assertEquals("lifecycle-ch", lunaSender.lastChannelId);
        assertEquals("msg-1", lunaSender.lastMessageId);
        assertEquals("Hello from Luna", lunaSender.lastContent);
        assertEquals(0, defaultSender.sendCalls.get());
    }

    @Test
    void send_whenNoLifecycleContext_usesDefaultSender() {
        router.send("main-ch", "msg-2", "Hello in main");

        assertEquals(1, defaultSender.sendCalls.get());
        assertEquals("main-ch", defaultSender.lastChannelId);
        assertEquals("msg-2", defaultSender.lastMessageId);
        assertEquals("Hello in main", defaultSender.lastContent);
        assertEquals(0, lunaSender.sendCalls.get());
    }

    @Test
    void send_whenLifecycleChannelButBotHasNoSender_doesNotSendNoFallback() {
        lifecycleContextStore.put(new LifecycleContext("ctx-2", "orphan-ch", NOW, null, "other-bot", null, null, null));
        // No router.registerSender("other-bot", ...) — so no sender for other-bot

        router.send("orphan-ch", "msg-3", "Should not be sent");

        assertEquals(0, defaultSender.sendCalls.get());
        assertEquals(0, lunaSender.sendCalls.get());
        // Router logs and returns; no fallback to default
    }

    @Test
    void send_whenChannelIdNull_doesNothing() {
        router.send(null, "msg", "content");
        assertEquals(0, defaultSender.sendCalls.get());
        assertEquals(0, lunaSender.sendCalls.get());
    }

    @Test
    void send_whenContentNull_doesNothing() {
        router.send("ch", "msg", null);
        assertEquals(0, defaultSender.sendCalls.get());
        assertEquals(0, lunaSender.sendCalls.get());
    }

    @Test
    void send_whenLifecycleContextHasBlankConfiguredBotId_usesDefaultSender() {
        lifecycleContextStore.put(new LifecycleContext("ctx-3", "ch-blank", NOW, null, "", null, null, null));

        router.send("ch-blank", "m", "Hi");

        assertEquals(1, defaultSender.sendCalls.get());
        assertEquals("Hi", defaultSender.lastContent);
        assertEquals(0, lunaSender.sendCalls.get());
    }

    @Test
    void getGatewayForChannel_withLifecycleContext_returnsThatBotsGateway() {
        DiscordGateway lunaGateway = new StubGateway();
        lifecycleContextStore.put(new LifecycleContext("ctx-4", "ch-gw", NOW, null, "luna", null, null, null));
        router.registerSender("luna", lunaSender, lunaGateway);

        assertSame(lunaGateway, router.getGatewayForChannel("ch-gw"));
    }

    @Test
    void getGatewayForChannel_withNoContext_returnsDefaultGateway() {
        DiscordGateway defaultGw = new StubGateway();
        router.setDefaultGateway(defaultGw);
        assertSame(defaultGw, router.getGatewayForChannel("any-ch"));
    }

    private static final class RecordingSender implements DiscordReplySender {
        final AtomicInteger sendCalls = new AtomicInteger(0);
        String lastChannelId;
        String lastMessageId;
        String lastContent;

        @Override
        public void send(String channelId, String messageId, String content) {
            sendCalls.incrementAndGet();
            this.lastChannelId = channelId;
            this.lastMessageId = messageId;
            this.lastContent = content;
        }
    }

    private static final class StubGateway implements DiscordGateway {
        @Override
        public void connect(java.util.function.Consumer<com.vinekeepers.events.Event> publisher) {}
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
        public boolean isConnected() { return false; }
    }
}
