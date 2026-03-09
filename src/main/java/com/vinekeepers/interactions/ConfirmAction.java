package com.vinekeepers.interactions;

/** Prompt text + confirm/cancel labels. */
public record ConfirmAction(String prompt, String confirmLabel, String cancelLabel) implements ResponseIntent {
    @Override
    public ResponseIntentType getType() {
        return ResponseIntentType.CONFIRM_ACTION;
    }
}
