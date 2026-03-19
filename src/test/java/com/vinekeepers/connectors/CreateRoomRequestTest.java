package com.vinekeepers.connectors;

import com.vinekeepers.events.Event;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for CreateRoomRequest.from() bind-then-state resolution.
 */
class CreateRoomRequestTest {

    @Test
    void from_nullEvent_setsSourceIdNull() {
        CreateRoomRequest req = CreateRoomRequest.from(null, Map.of("guildId", "g1"), Map.of());
        assertNull(req.sourceId());
    }

    @Test
    void from_setsSourceIdFromEvent() {
        Event event = new Event("discord:guild-1", "message", Map.of());
        CreateRoomRequest req = CreateRoomRequest.from(event, Map.of(), Map.of());
        assertEquals("discord:guild-1", req.sourceId());
    }

    @Test
    void from_guildId_bindTakesPrecedenceOverState() {
        CreateRoomRequest req = CreateRoomRequest.from(
                new Event("discord:x", "m", Map.of()),
                Map.of("guildId", "from-state"),
                Map.of("guildId", "from-bind"));
        assertEquals("from-bind", req.guildId());
    }

    @Test
    void from_guildId_fallsBackToStateWhenBindMissing() {
        CreateRoomRequest req = CreateRoomRequest.from(
                new Event("discord:x", "m", Map.of()),
                Map.of("guildId", "from-state"),
                Map.of());
        assertEquals("from-state", req.guildId());
    }

    @Test
    void from_channelName_bindTakesPrecedenceOverState() {
        CreateRoomRequest req = CreateRoomRequest.from(
                new Event("discord:x", "m", Map.of()),
                Map.of("channelName", "state-name"),
                Map.of("channelName", "bind-name"));
        assertEquals("bind-name", req.channelName());
    }

    @Test
    void from_lifecycleOwnerBotId_bindTakesPrecedenceOverState() {
        CreateRoomRequest req = CreateRoomRequest.from(
                new Event("discord:x", "m", Map.of()),
                Map.of("lifecycleOwnerBotId", "arrietty"),
                Map.of("lifecycleOwnerBotId", "luna"));
        assertEquals("luna", req.lifecycleOwnerBotId());
    }

    @Test
    void from_projectAndCodeChange_fromStateOnly() {
        CreateRoomRequest req = CreateRoomRequest.from(
                new Event("discord:x", "m", Map.of()),
                Map.of("project", "owner/repo", "codeChange", "phase-1"),
                Map.of());
        assertEquals("owner/repo", req.project());
        assertEquals("phase-1", req.codeChange());
    }

    @Test
    void from_fullConstructor_forTests() {
        CreateRoomRequest req = new CreateRoomRequest(
                "discord:g1", "g1", "my-channel", "luna", java.util.List.of(), "owner/repo", "feature-x");
        assertEquals("discord:g1", req.sourceId());
        assertEquals("g1", req.guildId());
        assertEquals("my-channel", req.channelName());
        assertEquals("luna", req.lifecycleOwnerBotId());
        assertEquals("owner/repo", req.project());
        assertEquals("feature-x", req.codeChange());
    }
}
