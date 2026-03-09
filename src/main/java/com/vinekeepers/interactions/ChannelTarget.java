package com.vinekeepers.interactions;

import java.util.Objects;

/** Target for channel/message (no interaction token). */
public record ChannelTarget(String sourceId, String channelId, String messageId) implements ReplyTarget {

    public ChannelTarget {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(channelId, "channelId");
        messageId = messageId != null ? messageId : "";
    }
}
