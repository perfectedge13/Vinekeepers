package com.vinekeepers.interactions;

import java.util.Objects;
import java.util.Optional;

/**
 * Outbound reply content: optional text and optional single intent. At least one must be present.
 * Workflows and reasoners produce this; engine passes to connector sink lifecycle methods.
 */
public final class OutboundResponse {

    private final String text;
    private final ResponseIntent intent;

    public OutboundResponse(String text, ResponseIntent intent) {
        boolean hasText = text != null && !text.isBlank();
        boolean hasIntent = intent != null;
        if (!hasText && !hasIntent) {
            throw new IllegalArgumentException("OutboundResponse must have at least one of text or intent");
        }
        this.text = text != null ? text : "";
        this.intent = intent;
    }

    public Optional<String> getText() {
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    public Optional<ResponseIntent> getIntent() {
        return Optional.ofNullable(intent);
    }

    /** Build from text only (backward compatibility). */
    public static OutboundResponse ofText(String text) {
        return new OutboundResponse(Objects.requireNonNull(text, "text"), null);
    }

    /** Build from intent only. */
    public static OutboundResponse ofIntent(ResponseIntent intent) {
        return new OutboundResponse("", Objects.requireNonNull(intent, "intent"));
    }

    /** Build from both. */
    public static OutboundResponse of(String text, ResponseIntent intent) {
        return new OutboundResponse(text != null ? text : "", intent);
    }
}
