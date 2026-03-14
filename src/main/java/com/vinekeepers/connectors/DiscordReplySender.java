package com.vinekeepers.connectors;

/**
 * Discord-specific reply sender. Extends the generic ReplySender contract so Discord
 * implementations can be used wherever the core expects ReplySender.
 */
public interface DiscordReplySender extends ReplySender {
}
