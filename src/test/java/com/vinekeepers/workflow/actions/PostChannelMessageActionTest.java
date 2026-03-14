package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.ReplySender;
import com.vinekeepers.workflow.actions.CreateThreadAction;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostChannelMessageActionTest {

    @Test
    void runReturnsErrorWhenReplySenderNull() {
        PostChannelMessageAction action = new PostChannelMessageAction(null);
        Object result = action.run(null, Map.of(), Map.of());
        assertEquals("Reply sender not available.", result);
    }

    @Test
    void runReturnsErrorWhenChannelIdMissing() {
        PostChannelMessageAction action = new PostChannelMessageAction((ch, msg, content) -> {});
        Object result = action.run(null, Map.of(), Map.of());
        assertEquals("Missing channelId for post_channel_message.", result);
    }

    @Test
    void runSendsContentFromBindThenState() {
        StringBuilder sent = new StringBuilder();
        ReplySender sender = (channelId, messageId, content) ->
                sent.append(channelId).append("|").append(content);
        PostChannelMessageAction action = new PostChannelMessageAction(sender);
        Object result = action.run(null, Map.of("channelId", "ch-1", "content", "Hello"),
                Map.of("channelId", "ch-1", "content", "Hello"));
        assertEquals("OK", result);
        assertEquals("ch-1|Hello", sent.toString());
    }

    @Test
    void runUsesStateWhenBindMissingChannelIdOrContent() {
        StringBuilder sent = new StringBuilder();
        ReplySender sender = (channelId, messageId, content) ->
                sent.append(channelId).append("|").append(content);
        PostChannelMessageAction action = new PostChannelMessageAction(sender);
        action.run(null, Map.of("channelId", "ch-state", "content", "From state"), Map.of());
        assertEquals("ch-state|From state", sent.toString());
    }

    @Test
    void runReturnsErrorWhenContentBlankAndDoesNotSend() {
        StringBuilder sent = new StringBuilder();
        ReplySender sender = (channelId, messageId, content) -> sent.append("sent");
        PostChannelMessageAction action = new PostChannelMessageAction(sender);
        Object result = action.run(null, Map.of("channelId", "ch-1"), Map.of("channelId", "ch-1"));
        assertEquals("Blank content for post_channel_message.", result);
        assertEquals("", sent.toString());
    }

    @Test
    void runInterpolatesContentFromBindState() {
        StringBuilder sent = new StringBuilder();
        ReplySender sender = (channelId, messageId, content) ->
                sent.append(channelId).append("|").append(content);
        PostChannelMessageAction action = new PostChannelMessageAction(sender);
        Map<String, Object> args = Map.of("channelId", "ch-lifecycle", "content",
                "Repo: {{project}}. Request: {{codeChange}}. Launching…", "project", "owner/repo", "codeChange", "Add feature X");
        action.run(null, Map.of("channelId", "ch-lifecycle"), args);
        assertEquals("ch-lifecycle|Repo: owner/repo. Request: Add feature X. Launching…", sent.toString());
    }

    @Test
    void runInterpolatesLifecycleBotNameFromMergedMapBindWins() {
        StringBuilder sent = new StringBuilder();
        ReplySender sender = (channelId, messageId, content) ->
                sent.append(channelId).append("|").append(content);
        PostChannelMessageAction action = new PostChannelMessageAction(sender);
        Map<String, Object> state = Map.of("channelId", "ch-1", "project", "acme/repo", "codeChange", "Phase 1", "lifecycleBotName", "StateBot");
        Map<String, Object> bind = Map.of("content", "Room handled by {{lifecycleBotName}}. Repo: {{project}}.", "lifecycleBotName", "Arrietty");
        action.run(null, state, bind);
        assertEquals("ch-1|Room handled by Arrietty. Repo: acme/repo.", sent.toString());
    }

    @Test
    void runFallsBackToChannelIdWhenDeliveryChannelIdIsThreadCreateFailed() {
        StringBuilder sent = new StringBuilder();
        ReplySender sender = (channelId, messageId, content) ->
                sent.append(channelId).append("|").append(content);
        PostChannelMessageAction action = new PostChannelMessageAction(sender);
        Map<String, Object> state = Map.of("channelId", "ch-room", "deliveryChannelId", CreateThreadAction.THREAD_CREATE_FAILED, "content", "Fallback to channel");
        action.run(null, state, Map.of());
        assertEquals("ch-room|Fallback to channel", sent.toString());
    }
}
