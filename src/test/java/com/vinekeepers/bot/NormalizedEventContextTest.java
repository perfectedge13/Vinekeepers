package com.vinekeepers.bot;

import com.vinekeepers.events.Event;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NormalizedEventContextTest {

    @Test
    void fromEventMapsPayloadAuthorToActorUsername() {
        Event event = new Event("discord:g:ch", "message",
                Map.of("author", "novawilde13_72571", "channelId", "ch1", "content", "hi"));
        NormalizedEventContext ctx = NormalizedEventContext.from(event);
        assertEquals("novawilde13_72571", ctx.getActorUsername());
    }

    @Test
    void fromEventMapsAuthorIdToActorId() {
        Event event = new Event("discord:g:ch", "message",
                Map.of("authorId", "123456", "author", "alice", "content", "hi"));
        NormalizedEventContext ctx = NormalizedEventContext.from(event);
        assertEquals("123456", ctx.getActorId());
        assertEquals("alice", ctx.getActorUsername());
    }

    @Test
    void fromEventWithNoAuthorReturnsNullActorUsername() {
        Event event = new Event("discord:g:ch", "message",
                Map.of("authorId", "u1", "content", "hi"));
        NormalizedEventContext ctx = NormalizedEventContext.from(event);
        assertEquals("u1", ctx.getActorId());
        assertNull(ctx.getActorUsername());
    }

    @Test
    void fromNullEventReturnsEmptyContext() {
        NormalizedEventContext ctx = NormalizedEventContext.from(null);
        assertNull(ctx.getActorId());
        assertNull(ctx.getActorUsername());
    }
}
