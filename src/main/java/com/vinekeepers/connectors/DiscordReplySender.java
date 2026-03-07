package com.vinekeepers.connectors;

/**
 * Sends messages back to Discord (reply or send to channel).
 * Used by the engine to deliver workflow replies to Discord.
 */
public interface DiscordReplySender {

    /**
     * Send or reply with the given content to the given channel.
     * messageId may be null for send-only; when present, may be used for reply reference.
     */
    void send(String channelId, String messageId, String content);
}
