package com.vinekeepers.interactions;

import java.util.Objects;

/** Target for an interaction (has token; may already be deferred by adapter). */
public record InteractionTarget(String sourceId, String channelId, String messageId,
                                String interactionId, String token, boolean alreadyDeferred) implements ReplyTarget {

    public InteractionTarget {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(channelId, "channelId");
        messageId = messageId != null ? messageId : "";
        Objects.requireNonNull(interactionId, "interactionId");
        Objects.requireNonNull(token, "token");
    }
}
