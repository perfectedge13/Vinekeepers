package com.vinekeepers.interactions;

/**
 * Identifies where a reply goes. Used by the engine to tell the connector which lifecycle operation to use.
 */
public sealed interface ReplyTarget permits ChannelTarget, InteractionTarget {

    String sourceId();
    String channelId();
    String messageId();
}
