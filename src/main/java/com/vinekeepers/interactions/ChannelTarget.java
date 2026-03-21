package com.vinekeepers.interactions;

import java.util.Objects;

/** Target for channel/message (no interaction token). */
public record ChannelTarget(String sourceId, String channelId, String messageId, String replyAsBotId)
        implements ReplyTarget {

    public ChannelTarget(String sourceId, String channelId, String messageId) {
        this(sourceId, channelId, messageId, null);
    }

    public ChannelTarget {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(channelId, "channelId");
        messageId = messageId != null ? messageId : "";
        replyAsBotId = replyAsBotId != null && !replyAsBotId.isBlank() ? replyAsBotId.trim() : null;
    }
}
