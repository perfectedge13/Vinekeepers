package com.vinekeepers.workflow.actions;

import java.util.Optional;

/**
 * Sends a channel message as a specific bot. Used by workflow actions and tests (see {@link EnsureRepoWorkspaceAction}).
 */
@FunctionalInterface
public interface ExplicitBotSender {

    /**
     * @return empty if sent; otherwise a short error message
     */
    Optional<String> sendAsExplicit(String channelId, String messageId, String content, String asBotId);
}
