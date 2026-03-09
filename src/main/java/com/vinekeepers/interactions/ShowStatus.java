package com.vinekeepers.interactions;

/** Status text + optional progress/summary. */
public record ShowStatus(String statusText, String progress) implements ResponseIntent {
    @Override
    public ResponseIntentType getType() {
        return ResponseIntentType.SHOW_STATUS;
    }
}
