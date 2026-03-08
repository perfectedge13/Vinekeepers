package com.vinekeepers.core.cursor;

/**
 * Adapter for the official Cursor Cloud Agent API.
 */
public interface CursorCloudAdapter {

    /**
     * Launch a new cloud agent run for the supplied repository and prompt.
     */
    CursorAgentLaunchResult launchAgent(CursorAgentLaunchRequest request);

    /**
     * Fetch the latest agent state, summary, branch, and PR details.
     */
    CursorAgentDetails getAgent(String agentId);

    /**
     * Fetch the conversation transcript for the given agent.
     */
    CursorAgentConversation getConversation(String agentId);

    /**
     * Send a follow-up prompt to an already running cloud agent.
     */
    void addFollowup(String agentId, String promptText);
}
