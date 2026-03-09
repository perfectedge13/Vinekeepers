package com.vinekeepers.interactions;

/**
 * Connector contract for sending replies. Distinct lifecycle operations; defer is adapter-internal only.
 * Engine calls respondImmediately, sendFollowUp, updateMessage, or openModal when it has content.
 */
public interface AppReplySink {

    /** Send full reply in the platform's initial response (e.g. Discord callback type 4). */
    void respondImmediately(OutboundResponse response, ReplyTarget target);

    /** Send content later using the interaction token (e.g. after adapter deferred). */
    void sendFollowUp(OutboundResponse response, ReplyTarget target);

    /** Edit the message that was the target of the interaction. */
    void updateMessage(OutboundResponse response, ReplyTarget target);

    /** Open a modal (e.g. Discord callback type 9). */
    void openModal(OutboundResponse response, ReplyTarget target);

    /** Declared capabilities for validation and fallback. */
    Capabilities getCapabilities();
}
