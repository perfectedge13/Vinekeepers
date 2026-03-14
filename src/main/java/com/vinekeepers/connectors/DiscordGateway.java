package com.vinekeepers.connectors;

import com.vinekeepers.events.Event;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Transport layer for Discord messaging and events.
 * Extends OutboundGateway for core send/channel/thread/permission operations;
 * adds interaction lifecycle: defer (adapter-internal), follow-up, update, modal.
 */
public interface DiscordGateway extends OutboundGateway {

    void connect(Consumer<Event> publisher);

    void shutdown();

    void send(String channelId, String messageId, String content);

    /**
     * Send to channel with optional components (list of action rows; each row is a list of component maps).
     * Default delegates to send(channelId, messageId, content) when components is null or empty.
     */
    default void send(String channelId, String messageId, String content, List<List<Map<String, Object>>> components) {
        send(channelId, messageId, content);
    }

    boolean isConnected();

    /**
     * Send follow-up content using a deferred interaction token.
     */
    default void sendFollowUp(String token, String content) {
        sendFollowUp(token, content, null);
    }

    /**
     * Send follow-up with optional components (list of action rows; each row is a list of component maps).
     */
    default void sendFollowUp(String token, String content, List<List<Map<String, Object>>> components) {
        // no-op by default
    }

    /**
     * Update the message that was the target of the interaction.
     */
    default void updateMessage(String token, String content) {
        updateMessage(token, content, null);
    }

    default void updateMessage(String token, String content, List<List<Map<String, Object>>> components) {
        // no-op by default
    }

    /**
     * Open a modal (e.g. for collect_form). Optional; no-op if not supported.
     */
    default void openModal(String interactionId, String token, String customId, String title, List<Map<String, Object>> fields) {
        // no-op by default
    }

    /**
     * Create a text channel in the given guild for lifecycle room. Returns channel id or null on failure.
     */
    default String createTextChannel(String guildId, String channelName) {
        return null;
    }

    /**
     * Create a thread in the given parent text channel. Returns the new thread's channel id or null on failure.
     */
    default String createThreadChannel(String parentChannelId, String threadName) {
        return null;
    }

    /**
     * Discord user id of the bot (self) for this gateway. Returns null if not connected or unknown.
     */
    default String getSelfUserId() {
        return null;
    }

    /**
     * Add or update a permission override for a user on a channel. allow and deny are Discord permission bits.
     * Completes synchronously so the workflow can rely on permissions before the first send.
     * @return true if the override was applied successfully, false if not supported, not connected, or on failure
     */
    default boolean addPermissionOverride(String channelId, String guildId, String targetUserId, long allow, long deny) {
        return false;
    }
}
