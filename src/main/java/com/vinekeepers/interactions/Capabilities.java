package com.vinekeepers.interactions;

import java.util.Set;

/**
 * Typed capabilities of a connector (supported intents, lifecycle, limits).
 * No stringly-typed capability sets.
 */
public record Capabilities(
        Set<ResponseIntentType> supportedIntents,
        boolean supportsInteractions,
        boolean supportsModalInput,
        boolean supportsMessageUpdate,
        boolean supportsEphemeralReplies,
        int deferRequiredWithinMs,
        Integer maxChoicesPerSelect,
        Integer maxButtonsPerRow,
        Integer maxFormFields
) {
    public Capabilities {
        supportedIntents = supportedIntents != null ? Set.copyOf(supportedIntents) : Set.of();
        maxChoicesPerSelect = maxChoicesPerSelect != null && maxChoicesPerSelect > 0 ? maxChoicesPerSelect : null;
        maxButtonsPerRow = maxButtonsPerRow != null && maxButtonsPerRow > 0 ? maxButtonsPerRow : null;
        maxFormFields = maxFormFields != null && maxFormFields > 0 ? maxFormFields : null;
    }

    public boolean supportsIntent(ResponseIntent intent) {
        return intent != null && supportedIntents.contains(intent.getType());
    }
}
