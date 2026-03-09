package com.vinekeepers.interactions;

/** Prompt text + optional placeholder / min-max length. */
public record CollectText(String prompt, String placeholder, Integer minLength, Integer maxLength) implements ResponseIntent {
    @Override
    public ResponseIntentType getType() {
        return ResponseIntentType.COLLECT_TEXT;
    }
}
