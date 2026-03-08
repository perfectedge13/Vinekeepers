package com.vinekeepers.core.cursor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Conversation transcript for a Cursor cloud agent.
 */
public record CursorAgentConversation(
        String id,
        List<CursorAgentMessage> messages
) {

    public CursorAgentConversation {
        messages = messages != null ? List.copyOf(messages) : List.of();
    }

    public Optional<CursorAgentMessage> latestAssistantMessage() {
        return messages.stream()
                .filter(Objects::nonNull)
                .filter(message -> "assistant_message".equals(message.type()))
                .reduce((first, second) -> second);
    }
}
