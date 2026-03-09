package com.vinekeepers.interactions;

import java.util.List;

/**
 * Platform-neutral response intent. Connectors render to native format (e.g. Discord components).
 */
public sealed interface ResponseIntent
        permits PresentChoices, ConfirmAction, CollectText, CollectForm, ShowActions, ShowStatus {

    ResponseIntentType getType();

    record Choice(String id, String label, String description) {}
    record FormField(String id, String label, String kind, boolean required, Integer minLength, Integer maxLength) {}
    record ActionItem(String id, String label, String style, String url) {}
}
