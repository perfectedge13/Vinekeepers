package com.vinekeepers.connectors;

import com.vinekeepers.events.Event;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Transport layer for Discord messaging and events.
 * Extended with interaction lifecycle: defer (adapter-internal), follow-up, update, modal.
 */
public interface DiscordGateway {

    void connect(Consumer<Event> publisher);

    void shutdown();

    void send(String channelId, String messageId, String content);

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
}
