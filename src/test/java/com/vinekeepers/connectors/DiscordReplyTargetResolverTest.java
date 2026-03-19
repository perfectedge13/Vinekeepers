package com.vinekeepers.connectors;

import com.vinekeepers.events.Event;
import com.vinekeepers.interactions.ChannelTarget;
import com.vinekeepers.interactions.InteractionTarget;
import com.vinekeepers.interactions.ReplyTarget;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordReplyTargetResolverTest {

    private final DiscordReplyTargetResolver resolver = new DiscordReplyTargetResolver();

    @Test
    void messageEventWithChannelIdAndMessageId_returnsChannelTargetWithThoseValues() {
        Event event = new Event("discord:g:123", "message",
                Map.of("channelId", "ch-1", "messageId", "msg-1", "content", "hi"));
        Optional<ReplyTarget> result = resolver.resolve(event);
        assertTrue(result.isPresent());
        ReplyTarget target = result.get();
        assertInstanceOf(ChannelTarget.class, target);
        ChannelTarget ct = (ChannelTarget) target;
        assertEquals("discord:g:123", ct.sourceId());
        assertEquals("ch-1", ct.channelId());
        assertEquals("msg-1", ct.messageId());
    }

    @Test
    void messageEventWithPayloadFallbackChannelAndMessageId_usesChannelAndMessageIdKeys() {
        Event event = new Event("discord:g:456", "message",
                Map.of("channel", "ch-fallback", "message_id", "msg-fallback", "content", "hi"));
        Optional<ReplyTarget> result = resolver.resolve(event);
        assertTrue(result.isPresent());
        ReplyTarget target = result.get();
        assertInstanceOf(ChannelTarget.class, target);
        ChannelTarget ct = (ChannelTarget) target;
        assertEquals("ch-fallback", ct.channelId());
        assertEquals("msg-fallback", ct.messageId());
    }

    @Test
    void interactionEventWithInteractionIdTokenDeferred_returnsInteractionTargetWithCorrectAlreadyDeferred() {
        Event event = new Event("discord:g:789", "interaction",
                Map.of("channelId", "ch-i", "interactionId", "int-1", "token", "tok-1", "deferred", true));
        Optional<ReplyTarget> result = resolver.resolve(event);
        assertTrue(result.isPresent());
        ReplyTarget target = result.get();
        assertInstanceOf(InteractionTarget.class, target);
        InteractionTarget it = (InteractionTarget) target;
        assertEquals("discord:g:789", it.sourceId());
        assertEquals("ch-i", it.channelId());
        assertEquals("int-1", it.interactionId());
        assertEquals("tok-1", it.token());
        assertTrue(it.alreadyDeferred());
    }

    @Test
    void interactionEventWithDeferredFalse_returnsInteractionTargetWithAlreadyDeferredFalse() {
        Event event = new Event("discord:g:789", "interaction",
                Map.of("channelId", "ch-i", "interactionId", "int-2", "token", "tok-2", "deferred", false));
        Optional<ReplyTarget> result = resolver.resolve(event);
        assertTrue(result.isPresent());
        ReplyTarget target = result.get();
        assertInstanceOf(InteractionTarget.class, target);
        InteractionTarget it = (InteractionTarget) target;
        assertEquals("int-2", it.interactionId());
        assertEquals("tok-2", it.token());
        assertEquals(false, it.alreadyDeferred());
    }

    @Test
    void interactionWithoutToken_returnsChannelTarget() {
        Event event = new Event("discord:g:ch", "interaction",
                Map.of("channelId", "ch-only", "interactionId", "int-3"));
        Optional<ReplyTarget> result = resolver.resolve(event);
        assertTrue(result.isPresent());
        ReplyTarget target = result.get();
        assertInstanceOf(ChannelTarget.class, target);
        ChannelTarget ct = (ChannelTarget) target;
        assertEquals("discord:g:ch", ct.sourceId());
        assertEquals("ch-only", ct.channelId());
    }

    @Test
    void sourceIdPassedThroughInChannelTarget() {
        Event event = new Event("discord:custom:abc", "message",
                Map.of("channelId", "ch-1", "content", "hi"));
        Optional<ReplyTarget> result = resolver.resolve(event);
        assertTrue(result.isPresent());
        assertEquals("discord:custom:abc", result.get().sourceId());
    }

    @Test
    void sourceIdPassedThroughInInteractionTarget() {
        Event event = new Event("discord:custom:xyz", "interaction",
                Map.of("channelId", "ch-1", "interactionId", "int-1", "token", "tok-1"));
        Optional<ReplyTarget> result = resolver.resolve(event);
        assertTrue(result.isPresent());
        assertEquals("discord:custom:xyz", result.get().sourceId());
    }
}
