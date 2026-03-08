package com.vinekeepers.core.cursor;

/**
 * A single Cursor cloud agent conversation message.
 */
public record CursorAgentMessage(
        String id,
        String type,
        String text
) {
}
