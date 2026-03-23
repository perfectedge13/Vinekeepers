package com.vinekeepers.connectors;

import com.vinekeepers.interactions.*;
import com.vinekeepers.state.LifecycleContext;
import com.vinekeepers.state.LifecycleContextStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.vinekeepers.interactions.ResponseIntent.Choice;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DiscordAppReplySinkTest {

    private RecordingDiscordGateway gateway;
    private DiscordAppReplySink sink;

    @BeforeEach
    void setUp() {
        gateway = new RecordingDiscordGateway();
        sink = new DiscordAppReplySink(gateway);
    }

    @Test
    void getCapabilitiesReturnsSupportedIntentsAndDeferMs() {
        Capabilities caps = sink.getCapabilities();
        assertTrue(caps.supportedIntents().contains(ResponseIntentType.PRESENT_CHOICES));
        assertTrue(caps.supportedIntents().contains(ResponseIntentType.CONFIRM_ACTION));
        assertTrue(caps.supportedIntents().contains(ResponseIntentType.COLLECT_TEXT));
        assertTrue(caps.supportsIntent(new PresentChoices("x", List.of())));
        assertTrue(caps.deferRequiredWithinMs() > 0);
    }

    @Test
    void respondImmediatelyWithChannelTargetCallsSend() {
        ChannelTarget target = new ChannelTarget("discord:g", "ch1", "msg1");
        sink.respondImmediately(OutboundResponse.ofText("Hi"), target);
        assertEquals(1, gateway.sendCalls.get());
        assertEquals("ch1|msg1|Hi", gateway.lastSend);
    }

    @Test
    void respondImmediatelyWithInteractionTargetNotDeferredCallsSendFollowUp() {
        InteractionTarget target = new InteractionTarget("discord:g", "ch1", "msg1", "int-id", "token-1", false);
        sink.respondImmediately(OutboundResponse.ofText("Reply"), target);
        assertEquals(1, gateway.sendFollowUpCalls.get());
        assertEquals("token-1|Reply", gateway.lastSendFollowUp);
    }

    @Test
    void respondImmediatelyWithInteractionTargetDeferredCallsSendFollowUp() {
        InteractionTarget target = new InteractionTarget("discord:g", "ch1", "msg1", "int-id", "token-2", true);
        sink.respondImmediately(OutboundResponse.ofText("Follow-up"), target);
        assertEquals(1, gateway.sendFollowUpCalls.get());
        assertEquals("token-2|Follow-up", gateway.lastSendFollowUp);
    }

    @Test
    void respondImmediatelyWithIntentRendersIntentAsTextWhenSupported() {
        ChannelTarget target = new ChannelTarget("discord:g", "ch1", "");
        ResponseIntent intent = new PresentChoices("Choose an option", List.of());
        sink.respondImmediately(OutboundResponse.ofIntent(intent), target);
        assertEquals("ch1||Choose an option", gateway.lastSend);
    }

    @Test
    void sendFollowUpRequiresInteractionTarget() {
        ChannelTarget target = new ChannelTarget("discord:g", "ch1", "");
        sink.sendFollowUp(OutboundResponse.ofText("x"), target);
        assertEquals(0, gateway.sendFollowUpCalls.get());
    }

    @Test
    void sendFollowUpWithInteractionTargetCallsGateway() {
        InteractionTarget target = new InteractionTarget("discord:g", "ch1", "m1", "i1", "tok", true);
        sink.sendFollowUp(OutboundResponse.ofText("Later"), target);
        assertEquals(1, gateway.sendFollowUpCalls.get());
        assertEquals("tok|Later", gateway.lastSendFollowUp);
    }

    @Test
    void updateMessageRequiresInteractionTarget() {
        ChannelTarget target = new ChannelTarget("discord:g", "ch1", "");
        sink.updateMessage(OutboundResponse.ofText("edit"), target);
        assertEquals(0, gateway.updateMessageCalls.get());
    }

    @Test
    void updateMessageWithInteractionTargetCallsGateway() {
        InteractionTarget target = new InteractionTarget("discord:g", "ch1", "m1", "i1", "tok", true);
        sink.updateMessage(OutboundResponse.ofText("Updated"), target);
        assertEquals(1, gateway.updateMessageCalls.get());
        assertEquals("tok|Updated", gateway.lastUpdateMessage);
    }

    @Test
    void openModalWithoutModalSupportSendsFollowUpAsFallback() {
        InteractionTarget target = new InteractionTarget("discord:g", "ch1", "m1", "i1", "tok", true);
        OutboundResponse response = OutboundResponse.ofIntent(new PresentChoices("Choose", List.of()));
        sink.openModal(response, target);
        assertEquals(1, gateway.sendFollowUpCalls.get());
        assertTrue(gateway.lastSendFollowUp.contains("Choose") || gateway.lastSendFollowUp.endsWith("Choose an option"));
    }

    @Test
    void intentToTextPresentChoicesUsesPromptOrDefault() {
        ChannelTarget target = new ChannelTarget("discord:g", "ch1", "");
        sink.respondImmediately(OutboundResponse.ofIntent(new PresentChoices("Pick one", List.of())), target);
        assertTrue(gateway.lastSend.endsWith("Pick one"));
    }

    @Test
    void intentToTextConfirmActionUsesPromptOrDefault() {
        ChannelTarget target = new ChannelTarget("discord:g", "ch1", "");
        sink.respondImmediately(OutboundResponse.ofIntent(new ConfirmAction("Proceed?", "Y", "N")), target);
        assertTrue(gateway.lastSend.endsWith("Proceed?"));
    }

    @Test
    void respondImmediatelyWithEmptyContentDoesNotCallSend() {
        ChannelTarget target = new ChannelTarget("discord:g", "ch1", "");
        sink.respondImmediately(OutboundResponse.of("", new ShowStatus("", null)), target);
        assertEquals(0, gateway.sendCalls.get());
    }

    @Test
    void respondImmediatelyWithChannelTargetAndPresentChoicesIntentCallsSendWithNonNullComponents() {
        ChannelTarget target = new ChannelTarget("discord:g", "ch-1", "msg-1");
        PresentChoices intent = new PresentChoices("Choose one", List.of(
                new Choice("opt1", "Option 1", null),
                new Choice("opt2", "Option 2", null)
        ));
        sink.respondImmediately(OutboundResponse.ofIntent(intent), target);
        assertEquals(1, gateway.sendCalls.get());
        assertEquals("ch-1|msg-1|Choose one", gateway.lastSend);
        assertNotNull(gateway.lastSendComponents);
        assertFalse(gateway.lastSendComponents.isEmpty());
    }

    @Test
    void routerUsesDefaultGatewayWhenLifecycleBotHasNoRegisteredGateway() {
        LifecycleContextStore lc = new LifecycleContextStore();
        Instant now = Instant.now();
        lc.put(new LifecycleContext(
                "ctx-1",
                "parent-ch",
                now,
                null,
                "bot-without-gateway",
                null,
                null,
                null,
                "ok",
                "thread-target"));
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(lc);
        RecordingDiscordGateway defaultGw = new RecordingDiscordGateway();
        router.setDefaultGateway(defaultGw);
        DiscordAppReplySink routerSink = new DiscordAppReplySink(router);
        InteractionTarget target = new InteractionTarget("discord:g", "thread-target", "m1", "i1", "tok", false);
        routerSink.respondImmediately(OutboundResponse.ofText("Hi"), target);
        assertEquals(1, defaultGw.sendFollowUpCalls.get());
        assertEquals("tok|Hi", defaultGw.lastSendFollowUp);
    }

    @Test
    void interactionTargetWithIngestBotIdUsesThatBotsGatewayNotDefault() {
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(new LifecycleContextStore());
        RecordingDiscordGateway defaultGw = new RecordingDiscordGateway();
        RecordingDiscordGateway gadgetGw = new RecordingDiscordGateway();
        router.setDefaultGateway(defaultGw);
        router.registerSender("gadget", gadgetGw::send, gadgetGw);
        DiscordAppReplySink routerSink = new DiscordAppReplySink(router);
        InteractionTarget target = new InteractionTarget("discord:g", "ch1", "m1", "i1", "tok", true, "gadget");
        routerSink.sendFollowUp(OutboundResponse.ofText("Later"), target);
        assertEquals(0, defaultGw.sendFollowUpCalls.get());
        assertEquals(1, gadgetGw.sendFollowUpCalls.get());
        assertEquals("tok|Later", gadgetGw.lastSendFollowUp);
    }

    @Test
    void interactionTargetWithIngestBotIdDoesNotFallBackToDefaultGatewayWhenBotGatewayMissing() {
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(new LifecycleContextStore());
        RecordingDiscordGateway defaultGw = new RecordingDiscordGateway();
        router.setDefaultGateway(defaultGw);
        DiscordAppReplySink routerSink = new DiscordAppReplySink(router);
        InteractionTarget target = new InteractionTarget("discord:g", "ch1", "m1", "i1", "tok", true, "gadget");
        routerSink.sendFollowUp(OutboundResponse.ofText("Later"), target);
        assertEquals(0, defaultGw.sendFollowUpCalls.get());
    }

    @Test
    void channelTargetWithReplyAsBotIdUsesThatBotsGatewayNotDefault() {
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(new LifecycleContextStore());
        RecordingDiscordGateway defaultGw = new RecordingDiscordGateway();
        RecordingDiscordGateway gadgetGw = new RecordingDiscordGateway();
        router.setDefaultGateway(defaultGw);
        router.registerSender("gadget", gadgetGw::send, gadgetGw);
        DiscordAppReplySink routerSink = new DiscordAppReplySink(router);
        ChannelTarget target = new ChannelTarget("discord:g", "ch1", "", "gadget");
        routerSink.respondImmediately(OutboundResponse.ofText("Deploy menu"), target);
        assertEquals(0, defaultGw.sendCalls.get());
        assertEquals(1, gadgetGw.sendCalls.get());
        assertTrue(gadgetGw.lastSend.startsWith("ch1|"));
    }

    private static final class RecordingDiscordGateway implements DiscordGateway {

        final AtomicInteger sendCalls = new AtomicInteger(0);
        final AtomicInteger sendFollowUpCalls = new AtomicInteger(0);
        final AtomicInteger updateMessageCalls = new AtomicInteger(0);
        String lastSend;
        String lastSendFollowUp;
        String lastUpdateMessage;
        private boolean connected;

        @Override
        public void connect(java.util.function.Consumer<com.vinekeepers.events.Event> publisher) {
            connected = true;
        }

        @Override
        public void shutdown() {
            connected = false;
        }

        List<List<Map<String, Object>>> lastSendComponents;

        @Override
        public void send(String channelId, String messageId, String content) {
            send(channelId, messageId, content, null);
        }

        @Override
        public void send(String channelId, String messageId, String content, List<List<Map<String, Object>>> components) {
            sendCalls.incrementAndGet();
            lastSend = channelId + "|" + (messageId != null ? messageId : "") + "|" + content;
            lastSendComponents = components;
        }

        @Override
        public void sendFollowUp(String token, String content) {
            sendFollowUp(token, content, null);
        }

        @Override
        public void sendFollowUp(String token, String content, List<List<Map<String, Object>>> components) {
            sendFollowUpCalls.incrementAndGet();
            lastSendFollowUp = token + "|" + content;
        }

        @Override
        public void updateMessage(String token, String content) {
            updateMessage(token, content, null);
        }

        @Override
        public void updateMessage(String token, String content, List<List<Map<String, Object>>> components) {
            updateMessageCalls.incrementAndGet();
            lastUpdateMessage = token + "|" + content;
        }

        @Override
        public boolean isConnected() {
            return connected;
        }
    }
}
