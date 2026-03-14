package com.vinekeepers.connectors;

/**
 * Generic contract for sending outbound replies (channel + optional message reference + content).
 * Connector-specific senders (e.g. Discord) implement this for use by the core engine and workflow actions.
 */
public interface ReplySender {

    /**
     * Send or reply with the given content to the given channel.
     * messageId may be null for send-only; when present, may be used for reply reference.
     */
    void send(String channelId, String messageId, String content);
}
