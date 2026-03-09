package com.vinekeepers.interactions;

import java.util.List;

/** Prompt text + list of actions (id, label, style, optional url). */
public record ShowActions(String prompt, List<ResponseIntent.ActionItem> actions) implements ResponseIntent {
    @Override
    public ResponseIntentType getType() {
        return ResponseIntentType.SHOW_ACTIONS;
    }
}
