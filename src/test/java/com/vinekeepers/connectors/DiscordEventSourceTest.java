package com.vinekeepers.connectors;

import com.vinekeepers.events.Event;
import com.vinekeepers.events.EventBus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordEventSourceTest {

    @Test
    void startThenStopSetsRunningFlag() {
        FakeDiscordGateway gateway = new FakeDiscordGateway();
        DiscordEventSource source = new DiscordEventSource(gateway);
        assertFalse(source.isRunning());
        source.start(new EventBus());
        assertTrue(source.isRunning());
        source.stop();
        assertFalse(source.isRunning());
    }

    @Test
    void sendDoesNotThrow() {
        FakeDiscordGateway gateway = new FakeDiscordGateway();
        DiscordEventSource source = new DiscordEventSource(gateway);
        source.send("ch1", "msg1", "Hello");
        source.send("ch2", null, "No reply id");
        assertEquals(2, gateway.sentMessages.size());
    }

    @Test
    void startPublishesGatewayMessageWithMentionsMetadata() {
        FakeDiscordGateway gateway = new FakeDiscordGateway();
        DiscordEventSource source = new DiscordEventSource(gateway);
        EventBus bus = new EventBus();
        List<Event> events = new ArrayList<>();
        bus.subscribe(events::add);

        source.start(bus);
        gateway.publish(new Event("discord:default", "message",
                java.util.Map.of("content", "ping @Luna", "channelId", "stub", "authorId", "user", "author", "novawilde13_72571", "mentions", List.of("luna"))));

        assertEquals(1, events.size());
        Event event = events.getFirst();
        assertEquals("discord:default", event.getSourceId());
        assertEquals("message", event.getKind());
        assertNotNull(event.getPayload().get("mentions"));
        assertInstanceOf(List.class, event.getPayload().get("mentions"));
        assertEquals("novawilde13_72571", event.getPayload().get("author"));
    }

    @Test
    void getReplySinkReturnsDiscordAppReplySink() {
        FakeDiscordGateway gateway = new FakeDiscordGateway();
        DiscordEventSource source = new DiscordEventSource(gateway);
        DiscordAppReplySink sink = source.getReplySink();
        assertNotNull(sink);
        assertInstanceOf(DiscordAppReplySink.class, sink);
        assertNotNull(sink.getCapabilities());
    }

    private static final class FakeDiscordGateway implements DiscordGateway {

        private Consumer<Event> publisher;
        private boolean connected;
        private final List<String> sentMessages = new ArrayList<>();

        @Override
        public void connect(Consumer<Event> publisher) {
            this.publisher = publisher;
            this.connected = true;
        }

        @Override
        public void shutdown() {
            this.connected = false;
        }

        @Override
        public void send(String channelId, String messageId, String content) {
            sentMessages.add(channelId + "|" + messageId + "|" + content);
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        private void publish(Event event) {
            publisher.accept(event);
        }
    }
}
