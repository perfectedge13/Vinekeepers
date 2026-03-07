package com.vinekeepers.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventBusTest {

    private EventBus bus;
    private List<Event> received;

    @BeforeEach
    void setUp() {
        bus = new EventBus();
        received = new ArrayList<>();
    }

    @Test
    void publishDeliversToSubscriber() {
        bus.subscribe(received::add);
        Event event = new Event("source-1", "kind-1", Map.of());
        bus.publish(event);
        assertEquals(1, received.size());
        assertEquals("source-1", received.get(0).getSourceId());
        assertEquals("kind-1", received.get(0).getKind());
    }

    @Test
    void unsubscribeStopsDelivery() {
        bus.subscribe(received::add);
        EventSubscriber sub = received::add;
        bus.subscribe(sub);
        bus.unsubscribe(sub);
        bus.publish(new Event("s", "k", Map.of()));
        assertEquals(1, received.size());
    }

    @Test
    void multipleSubscribersAllReceive() {
        List<Event> second = new ArrayList<>();
        bus.subscribe(received::add);
        bus.subscribe(second::add);
        Event event = new Event("s", "k", Map.of());
        bus.publish(event);
        assertEquals(1, received.size());
        assertEquals(1, second.size());
        assertEquals(event, received.get(0));
        assertEquals(event, second.get(0));
    }
}
