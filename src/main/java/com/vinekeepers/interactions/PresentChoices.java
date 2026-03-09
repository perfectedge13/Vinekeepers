package com.vinekeepers.interactions;

import java.util.List;

/** Prompt text + list of choices (id, label, optional description). */
public record PresentChoices(String prompt, List<ResponseIntent.Choice> choices) implements ResponseIntent {
    @Override
    public ResponseIntentType getType() {
        return ResponseIntentType.PRESENT_CHOICES;
    }
}
