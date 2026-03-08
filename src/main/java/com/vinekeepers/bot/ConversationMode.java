package com.vinekeepers.bot;

/**
 * Runtime mode for configurable workflows.
 */
public enum ConversationMode {
    SINGLE_EVENT,
    CONVERSATIONAL;

    public static ConversationMode fromValue(String value) {
        if ("conversational".equalsIgnoreCase(value)) {
            return CONVERSATIONAL;
        }
        return SINGLE_EVENT;
    }
}
