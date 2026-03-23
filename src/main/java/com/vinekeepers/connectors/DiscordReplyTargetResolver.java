package com.vinekeepers.connectors;

import com.vinekeepers.events.Event;
import com.vinekeepers.interactions.ChannelTarget;
import com.vinekeepers.interactions.InteractionTarget;
import com.vinekeepers.interactions.ReplyTarget;

import java.util.Optional;

/**
 * Resolves Discord reply target from event payload (channelId, messageId, interactionId, token, deferred).
 * Matches previous engine logic: interaction kind with interactionId and token → InteractionTarget, else ChannelTarget.
 */
public final class DiscordReplyTargetResolver implements ReplyTargetResolver {

    @Override
    public Optional<ReplyTarget> resolve(Event event) {
        String sourceId = event.getSourceId();
        String channelId = event.getPayload("channelId", String.class);
        if (channelId == null) {
            channelId = event.getPayload("channel", String.class);
        }
        if (channelId == null) {
            channelId = "";
        }
        String messageId = event.getPayload("messageId", String.class);
        if (messageId == null) {
            messageId = event.getPayload("message_id", String.class);
        }
        if (messageId == null) {
            messageId = "";
        }
        Boolean deferred = event.getPayload("deferred", Boolean.class);
        String interactionId = event.getPayload("interactionId", String.class);
        String token = event.getPayload("token", String.class);
        String ingestBotId = event.getPayload("ingestBotId", String.class);
        if ("interaction".equals(event.getKind()) && interactionId != null && token != null) {
            return Optional.of(new InteractionTarget(
                    sourceId, channelId, messageId, interactionId, token, Boolean.TRUE.equals(deferred), ingestBotId));
        }
        return Optional.of(new ChannelTarget(sourceId, channelId, messageId));
    }
}
