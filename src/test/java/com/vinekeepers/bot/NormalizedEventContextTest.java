package com.vinekeepers.bot;

import com.vinekeepers.events.Event;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void fromEventMapsParentChannelIdForThreadPayload() {
        Event event = new Event("discord:g:thread", "message",
                Map.of("channelId", "thread-1", "parentChannelId", "ops-chan", "threadId", "thread-1", "content", "hi"));
        NormalizedEventContext ctx = NormalizedEventContext.from(event);
        assertEquals("ops-chan", ctx.getParentChannelId());
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

    @Test
    void mentionsFromTextIgnoreRoleStyleAngleBrackets() {
        Event event = new Event("discord:g:ch", "message",
                Map.of("content", "Ping <@&987654321098765432> team"));
        NormalizedEventContext ctx = NormalizedEventContext.from(event);
        assertTrue(ctx.getMentions().isEmpty());
    }

    @Test
    void mentionsFromTextIncludeNumericUserOrBotIdFromAngleBrackets() {
        Event event = new Event("discord:g:ch", "message",
                Map.of("content", "Hi <@!111222333444555666> there"));
        NormalizedEventContext ctx = NormalizedEventContext.from(event);
        assertTrue(ctx.getMentions().contains("111222333444555666"));
    }

    @Test
    void mentionsFromTextIncludeNumericUserIdFromPlainUserMentionAngleBrackets() {
        Event event = new Event("discord:g:ch", "message",
                Map.of("content", "Hi <@111222333444555666> there"));
        NormalizedEventContext ctx = NormalizedEventContext.from(event);
        assertTrue(ctx.getMentions().contains("111222333444555666"));
    }

    @Test
    void mentionsFromTextDoNotTreatPseudoUserAngleBracketsAsNames() {
        Event event = new Event("discord:g:ch", "message",
                Map.of("content", "Not a real ping: <@gadget>"));
        NormalizedEventContext ctx = NormalizedEventContext.from(event);
        assertFalse(ctx.getMentions().contains("gadget"));
    }

    @Test
    void mentionsMergePayloadListWithParsedText() {
        Event event = new Event("discord:g:ch", "message",
                Map.of("content", "Also @Other", "mentions", List.of("FromMeta")));
        NormalizedEventContext ctx = NormalizedEventContext.from(event);
        assertTrue(ctx.getMentions().contains("frommeta"));
        assertTrue(ctx.getMentions().contains("other"));
    }
}
