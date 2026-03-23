package com.vinekeepers.connectors;

import com.vinekeepers.state.LifecycleContext;
import com.vinekeepers.state.LifecycleContextStore;
import com.vinekeepers.state.planning.FeatureRoomState;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.planning.PlanningRole;
import com.vinekeepers.state.planning.RoomParticipant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
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
        OutboundGateway lunaGateway = new StubGateway();
        lifecycleContextStore.put(new LifecycleContext("ctx-4", "ch-gw", NOW, null, "luna", null, null, null));
        router.registerSender("luna", lunaSender, lunaGateway);

        assertSame(lunaGateway, router.getGatewayForChannel("ch-gw"));
    }

    @Test
    void getGatewayForChannel_withNoContext_returnsDefaultGateway() {
        OutboundGateway defaultGw = new StubGateway();
        router.setDefaultGateway(defaultGw);
        assertSame(defaultGw, router.getGatewayForChannel("any-ch"));
    }

    @Test
    void getDefaultGateway_returnsSetDefaultGateway() {
        OutboundGateway defaultGw = new StubGateway();
        router.setDefaultGateway(defaultGw);
        assertSame(defaultGw, router.getDefaultGateway());
    }

    @Test
    void getGatewayForChannel_withLifecycleContextButBotHasNoGateway_returnsNull() {
        lifecycleContextStore.put(new LifecycleContext("ctx-5", "ch-no-gw", NOW, null, "other-bot", null, null, null));
        router.registerSender("other-bot", lunaSender, null); // sender but no gateway
        OutboundGateway defaultGw = new StubGateway();
        router.setDefaultGateway(defaultGw);

        assertNull(router.getGatewayForChannel("ch-no-gw"));
    }

    @Test
    void getSelfUserIdForBot_returnsUserIdWhenBotHasGateway() {
        StubGatewayWithSelfId lunaGateway = new StubGatewayWithSelfId("luna-discord-user-id");
        router.registerSender("luna", lunaSender, lunaGateway);

        assertEquals("luna-discord-user-id", router.getSelfUserIdForBot("luna"));
    }

    @Test
    void getSelfUserIdForBot_returnsNullWhenBotHasNoGateway() {
        assertNull(router.getSelfUserIdForBot("unknown-bot"));
    }

    @Test
    void getSelfUserIdForBot_returnsNullWhenBotIdNullOrBlank() {
        assertNull(router.getSelfUserIdForBot(null));
        assertNull(router.getSelfUserIdForBot(""));
        assertNull(router.getSelfUserIdForBot("   "));
    }

    @Test
    void send_whenTargetIsThreadIdResolvesContextByDeliveryTargetId_usesThatBotsSender() {
        LifecycleContext ctx = new LifecycleContext("ctx-1", "chan-parent", NOW, null, "luna", null, null, null, null, "thread-456");
        lifecycleContextStore.put(ctx);
        router.registerSender("luna", lunaSender, null);

        router.send("thread-456", null, "Update in thread");

        assertEquals(1, lunaSender.sendCalls.get());
        assertEquals("thread-456", lunaSender.lastChannelId);
        assertEquals(null, lunaSender.lastMessageId);
        assertEquals("Update in thread", lunaSender.lastContent);
        assertEquals(0, defaultSender.sendCalls.get());
    }

    @Test
    void send_whenTargetIsThreadCreateFailed_doesNotResolveAsContext() {
        lifecycleContextStore.put(new LifecycleContext("ctx-1", "chan-1", NOW, null, "luna", null, null, null, null, "THREAD_CREATE_FAILED"));
        router.registerSender("luna", lunaSender, null);

        router.send("THREAD_CREATE_FAILED", null, "content");

        assertEquals(0, lunaSender.sendCalls.get());
        assertEquals(1, defaultSender.sendCalls.get());
        assertEquals("THREAD_CREATE_FAILED", defaultSender.lastChannelId);
    }

    @Test
    void sendAs_usesSpecifiedBotSender() {
        RecordingSender architectSender = new RecordingSender();
        router.registerSender("luna", lunaSender, null);
        router.registerSender("architect", architectSender, null);

        router.sendAs("ch-1", "msg-1", "From architect", "architect");

        assertEquals(1, architectSender.sendCalls.get());
        assertEquals("ch-1", architectSender.lastChannelId);
        assertEquals("From architect", architectSender.lastContent);
        assertEquals(0, lunaSender.sendCalls.get());
        assertEquals(0, defaultSender.sendCalls.get());
    }

    @Test
    void sendAs_whenAsBotIdNullOrBlank_fallsBackToStandardSend() {
        lifecycleContextStore.put(new LifecycleContext("ctx-1", "ch-1", NOW, null, "luna", null, null, null));
        router.registerSender("luna", lunaSender, null);

        router.sendAs("ch-1", null, "content", null);
        assertEquals(1, lunaSender.sendCalls.get());
        lunaSender.sendCalls.set(0);
        router.sendAs("ch-1", null, "content", "");
        assertEquals(1, lunaSender.sendCalls.get());
    }

    @Test
    void sendAs_whenNoSenderForBot_doesNotSend() {
        router.registerSender("luna", lunaSender, null);
        router.sendAs("ch-1", null, "content", "unknown-bot");
        assertEquals(0, lunaSender.sendCalls.get());
        assertEquals(0, defaultSender.sendCalls.get());
    }

    @Test
    void sendAsRole_resolvesRoleToBotAndSendsViaThatBot() {
        FeatureRoomStateStore featureStore = new FeatureRoomStateStore();
        List<RoomParticipant> participants = List.of(
                new RoomParticipant(PlanningRole.ORCHESTRATOR, "arrietty", "i-o", "Arrietty", true));
        FeatureRoomState roomState = new FeatureRoomState(
                "ctx-1", null, null, "room-ch-1", "thread-1", null, null, "INTAKE_READY",
                participants, null, null);
        featureStore.put(roomState);
        OutboundDeliveryRouter routerWithFeature = new OutboundDeliveryRouter(lifecycleContextStore, featureStore);
        routerWithFeature.setDefaultSender(defaultSender);
        RecordingSender arriettySender = new RecordingSender();
        routerWithFeature.registerSender("arrietty", arriettySender, null);

        routerWithFeature.sendAsRole("room-ch-1", null, "Coordinator report", PlanningRole.ORCHESTRATOR);

        assertEquals(1, arriettySender.sendCalls.get());
        assertEquals("room-ch-1", arriettySender.lastChannelId);
        assertEquals("Coordinator report", arriettySender.lastContent);
    }

    @Test
    void sendAsRole_whenTargetIsThread_resolvesByDeliveryTargetId() {
        FeatureRoomStateStore featureStore = new FeatureRoomStateStore();
        List<RoomParticipant> participants = List.of(
                new RoomParticipant(PlanningRole.ORCHESTRATOR, "arrietty", "i-o", "Arrietty", true));
        FeatureRoomState roomState = new FeatureRoomState(
                "ctx-1", null, null, "room-ch", "intake-thread-99", null, null, "INTAKE_READY",
                participants, null, null);
        featureStore.put(roomState);
        OutboundDeliveryRouter routerWithFeature = new OutboundDeliveryRouter(lifecycleContextStore, featureStore);
        routerWithFeature.setDefaultSender(defaultSender);
        RecordingSender arriettySender = new RecordingSender();
        routerWithFeature.registerSender("arrietty", arriettySender, null);

        routerWithFeature.sendAsRole("intake-thread-99", null, "Arrietty reply", PlanningRole.ORCHESTRATOR);

        assertEquals(1, arriettySender.sendCalls.get());
        assertEquals("Arrietty reply", arriettySender.lastContent);
    }

    @Test
    void sendAsRole_whenNoFeatureRoomOrNullRole_fallsBackToStandardSend() {
        OutboundDeliveryRouter routerWithFeature = new OutboundDeliveryRouter(lifecycleContextStore, null);
        routerWithFeature.setDefaultSender(defaultSender);
        routerWithFeature.sendAsRole("ch-1", null, "content", PlanningRole.ORCHESTRATOR);
        assertEquals(1, defaultSender.sendCalls.get());
    }

    private static final class RecordingSender implements ReplySender {
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

    private static class StubGateway implements OutboundGateway {
        @Override
        public void send(String channelId, String messageId, String content) {}
        @Override
        public String getSelfUserId() { return null; }
        @Override
        public boolean isConnected() { return false; }
        @Override
        public String createTextChannel(String guildId, String channelName) { return null; }
        @Override
        public String createThreadChannel(String parentChannelId, String threadName) { return null; }
        @Override
        public boolean addPermissionOverride(String channelId, String guildId, String targetUserId, long allow, long deny) { return true; }
    }

    private static final class StubGatewayWithSelfId extends StubGateway {
        private final String selfUserId;

        StubGatewayWithSelfId(String selfUserId) {
            this.selfUserId = selfUserId;
        }

        @Override
        public String getSelfUserId() {
            return selfUserId;
        }
    }
}
