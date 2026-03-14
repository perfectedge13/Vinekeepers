package com.vinekeepers.connectors;

/**
 * Small transitional generic gateway for outbound delivery: send text, get self identity,
 * create channels/threads, and manage permission overrides. Connector-specific gateways
 * (e.g. Discord) extend this; interaction-specific methods remain on the connector interface.
 */
public interface OutboundGateway {

    void send(String channelId, String messageId, String content);

    /**
     * User id of the bot (self) for this gateway. Returns null if not connected or unknown.
     */
    String getSelfUserId();

    boolean isConnected();

    /**
     * Create a text channel in the given guild. Returns channel id or null on failure.
     */
    String createTextChannel(String guildId, String channelName);

    /**
     * Create a thread in the given parent text channel. Returns the new thread's channel id or null on failure.
     */
    String createThreadChannel(String parentChannelId, String threadName);

    /**
     * Add or update a permission override for a user on a channel (e.g. allow/deny bits).
     * @return true if the override was applied successfully, false otherwise
     */
    boolean addPermissionOverride(String channelId, String guildId, String targetUserId, long allow, long deny);
}
