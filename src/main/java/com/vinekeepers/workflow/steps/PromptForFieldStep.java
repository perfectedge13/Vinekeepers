package com.vinekeepers.workflow.steps;

import com.vinekeepers.events.Event;
import com.vinekeepers.interactions.ConfirmAction;
import com.vinekeepers.interactions.OutboundResponse;
import com.vinekeepers.interactions.PresentChoices;
import com.vinekeepers.interactions.ResponseIntent;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepResult;
import com.vinekeepers.workflow.WorkflowStep;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Conversational step that prompts once and pauses the workflow until a later event supplies the field.
 * Optional intent/choices/confirmLabel/cancelLabel/fields produce a rich OutboundResponse when supported.
 */
public final class PromptForFieldStep implements WorkflowStep {

    private final String prompt;
    private final String storeIn;
    private final String intent;
    private final List<Map<String, Object>> choices;
    private final String confirmLabel;
    private final String cancelLabel;
    private final List<Map<String, Object>> fields;

    public PromptForFieldStep(String prompt, String storeIn) {
        this(prompt, storeIn, null, null, null, null, null);
    }

    public PromptForFieldStep(String prompt, String storeIn, String intent,
                             List<Map<String, Object>> choices, String confirmLabel, String cancelLabel,
                             List<Map<String, Object>> fields) {
        this.prompt = prompt != null ? prompt : "";
        this.storeIn = storeIn != null ? storeIn : "input";
        this.intent = intent;
        this.choices = choices != null ? List.copyOf(choices) : List.of();
        this.confirmLabel = confirmLabel;
        this.cancelLabel = cancelLabel;
        this.fields = fields != null ? List.copyOf(fields) : List.of();
    }

    @Override
    public StepResult execute(Event event, ConfigurableWorkflowState state, int stepIndex) {
        Object existing = state != null ? state.get(storeIn) : null;
        if (existing instanceof String value && !value.isBlank()) {
            return StepResult.advance(null, null);
        }
        if (existing != null) {
            return StepResult.advance(null, null);
        }
        OutboundResponse richReply = buildRichReply();
        if (richReply != null) {
            return StepResult.waiting(prompt, storeIn, richReply);
        }
        return StepResult.waiting(prompt, storeIn);
    }

    private OutboundResponse buildRichReply() {
        if ("present_choices".equals(intent) && !choices.isEmpty()) {
            List<ResponseIntent.Choice> cs = choices.stream()
                    .map(m -> new ResponseIntent.Choice(
                            stringVal(m, "id"),
                            stringVal(m, "label"),
                            stringVal(m, "description")))
                    .collect(Collectors.toList());
            return OutboundResponse.of(prompt, new PresentChoices(prompt, cs));
        }
        if ("confirm_action".equals(intent)) {
            return OutboundResponse.of(prompt, new ConfirmAction(
                    prompt,
                    confirmLabel != null ? confirmLabel : "Confirm",
                    cancelLabel != null ? cancelLabel : "Cancel"));
        }
        return null;
    }

    private static String stringVal(Map<String, Object> m, String key) {
        if (m == null) return null;
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }
}
