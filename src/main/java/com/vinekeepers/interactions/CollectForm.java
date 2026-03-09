package com.vinekeepers.interactions;

import java.util.List;

/** Title + list of fields (id, label, kind, required, min/max). */
public record CollectForm(String title, List<ResponseIntent.FormField> fields) implements ResponseIntent {
    @Override
    public ResponseIntentType getType() {
        return ResponseIntentType.COLLECT_FORM;
    }
}
