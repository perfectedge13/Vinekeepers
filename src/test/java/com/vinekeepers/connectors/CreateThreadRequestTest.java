package com.vinekeepers.connectors;

import com.vinekeepers.events.Event;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for CreateThreadRequest.from() bind-then-state resolution.
 */
class CreateThreadRequestTest {

    @Test
    void from_nullEvent_setsSourceIdNull() {
        CreateThreadRequest req = CreateThreadRequest.from(null, Map.of("channelId", "ch1"), Map.of());
        assertNull(req.sourceId());
    }

    @Test
    void from_setsSourceIdFromEvent() {
        Event event = new Event("discord:ch", "message", Map.of());
        CreateThreadRequest req = CreateThreadRequest.from(event, Map.of("channelId", "ch1"), Map.of());
        assertEquals("discord:ch", req.sourceId());
    }

    @Test
    void from_channelId_bindTakesPrecedenceOverState() {
        CreateThreadRequest req = CreateThreadRequest.from(
                new Event("discord:x", "m", Map.of()),
                Map.of("channelId", "chan-state"),
                Map.of("channelId", "chan-bind"));
        assertEquals("chan-bind", req.channelId());
    }

    @Test
    void from_channelId_fallsBackToStateWhenBindMissing() {
        CreateThreadRequest req = CreateThreadRequest.from(
                new Event("discord:x", "m", Map.of()),
                Map.of("channelId", "chan-state"),
                Map.of());
        assertEquals("chan-state", req.channelId());
    }

    @Test
    void from_threadName_bindTakesPrecedenceOverState() {
        CreateThreadRequest req = CreateThreadRequest.from(
                new Event("discord:x", "m", Map.of()),
                Map.of("channelId", "ch", "threadName", "State thread"),
                Map.of("threadName", "Bind thread"));
        assertEquals("Bind thread", req.threadName());
    }

    @Test
    void from_contextId_bindTakesPrecedenceOverState() {
        CreateThreadRequest req = CreateThreadRequest.from(
                new Event("discord:x", "m", Map.of()),
                Map.of("channelId", "ch", "contextId", "ctx-state"),
                Map.of("contextId", "ctx-bind"));
        assertEquals("ctx-bind", req.contextId());
    }

    @Test
    void from_fullConstructor_forTests() {
        CreateThreadRequest req = new CreateThreadRequest("discord:ch", "chan-1", "Room updates", "ctx-1");
        assertEquals("discord:ch", req.sourceId());
        assertEquals("chan-1", req.channelId());
        assertEquals("Room updates", req.threadName());
        assertEquals("ctx-1", req.contextId());
    }
}
