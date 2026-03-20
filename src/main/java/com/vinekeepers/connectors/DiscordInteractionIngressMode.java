package com.vinekeepers.connectors;

/**
 * How a bot's Discord gateway publishes {@code interaction} events to the engine.
 */
public enum DiscordInteractionIngressMode {
    /** No interaction listener. */
    NONE,
    /** Full interaction ingress (routed bots; same effective scope as {@link #OWN_MESSAGES} for component events). */
    ROUTED,
    /** Component interactions for this application (no extra channel filter; Discord scopes by app). */
    OWN_MESSAGES,
    /** Publish interactions only when the channel/thread is an owned feature room or legacy lifecycle channel. */
    OWNED_SPACES
}
