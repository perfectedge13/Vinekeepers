package com.vinekeepers.connectors;

/**
 * How a bot's Discord gateway publishes {@code message} events to the engine.
 */
public enum DiscordMessageIngressMode {
    /** No message listener. */
    NONE,
    /** Publish all non-bot messages the gateway receives (YAML-routed intake bots). */
    ROUTED,
    /** Publish only when the channel/thread is a known feature room or legacy lifecycle channel for this bot. */
    OWNED_SPACES
}
