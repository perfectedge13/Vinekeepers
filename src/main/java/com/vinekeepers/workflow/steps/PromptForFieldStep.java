package com.vinekeepers.workflow.steps;

import com.vinekeepers.events.Event;
import com.vinekeepers.interactions.ConfirmAction;
import com.vinekeepers.interactions.OutboundResponse;
import com.vinekeepers.interactions.PresentChoices;
import com.vinekeepers.interactions.ResponseIntent;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.DynamicChoiceProvider;
import com.vinekeepers.workflow.StepResult;
import com.vinekeepers.workflow.WorkflowStep;
import com.vinekeepers.workflow.template.WorkflowTemplateInterpolator;
import com.vinekeepers.workflow.template.WorkflowTemplatePolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Conversational step that prompts once and pauses the workflow until a later event supplies the field.
 * Optional intent/choices/confirmLabel/cancelLabel/fields produce a rich OutboundResponse when supported.
 * When choiceProvider is set, dynamic choices are fetched and merged with static choices.
 * Optional {@code control.mode} from YAML (e.g. {@code plain_text}) overrides rich controls for config-driven UX.
 */
public final class PromptForFieldStep implements WorkflowStep {

    private final String prompt;
    private final String storeIn;
    private final String intent;
    private final List<Map<String, Object>> choices;
    private final String confirmLabel;
    private final String cancelLabel;
    private final List<Map<String, Object>> fields;
    private final DynamicChoiceProvider choiceProvider;
    /** e.g. {@code plain_text} — force text-only wait even if intent/choiceProvider would add components. */
    private final String controlMode;
    private final WorkflowTemplatePolicy templatePolicy;

    public PromptForFieldStep(String prompt, String storeIn) {
        this(prompt, storeIn, null, null, null, null, null, null, null, WorkflowTemplatePolicy.LEGACY_FULL_STATE);
    }

    public PromptForFieldStep(String prompt, String storeIn, String intent,
                             List<Map<String, Object>> choices, String confirmLabel, String cancelLabel,
                             List<Map<String, Object>> fields) {
        this(prompt, storeIn, intent, choices, confirmLabel, cancelLabel, fields, null, null,
                WorkflowTemplatePolicy.LEGACY_FULL_STATE);
    }

    public PromptForFieldStep(String prompt, String storeIn, String intent,
                             List<Map<String, Object>> choices, String confirmLabel, String cancelLabel,
                             List<Map<String, Object>> fields, DynamicChoiceProvider choiceProvider) {
        this(prompt, storeIn, intent, choices, confirmLabel, cancelLabel, fields, choiceProvider, null,
                WorkflowTemplatePolicy.LEGACY_FULL_STATE);
    }

    public PromptForFieldStep(String prompt, String storeIn, String intent,
                             List<Map<String, Object>> choices, String confirmLabel, String cancelLabel,
                             List<Map<String, Object>> fields, DynamicChoiceProvider choiceProvider,
                             String controlMode) {
        this(prompt, storeIn, intent, choices, confirmLabel, cancelLabel, fields, choiceProvider, controlMode,
                WorkflowTemplatePolicy.LEGACY_FULL_STATE);
    }

    public PromptForFieldStep(String prompt, String storeIn, String intent,
                             List<Map<String, Object>> choices, String confirmLabel, String cancelLabel,
                             List<Map<String, Object>> fields, DynamicChoiceProvider choiceProvider,
                             String controlMode,
                             WorkflowTemplatePolicy templatePolicy) {
        this.prompt = prompt != null ? prompt : "";
        this.storeIn = storeIn != null ? storeIn : "input";
        this.intent = intent;
        this.choices = choices != null ? List.copyOf(choices) : List.of();
        this.confirmLabel = confirmLabel;
        this.cancelLabel = cancelLabel;
        this.fields = fields != null ? List.copyOf(fields) : List.of();
        this.choiceProvider = choiceProvider;
        this.controlMode = controlMode != null && !controlMode.isBlank() ? controlMode.trim() : null;
        this.templatePolicy = templatePolicy != null ? templatePolicy : WorkflowTemplatePolicy.LEGACY_FULL_STATE;
    }

    @Override
    public StepResult execute(Event event, ConfigurableWorkflowState state, int stepIndex) {
        Object existing = state != null ? state.get(storeIn) : null;
        if (existing instanceof String value && !value.isBlank() && !"__custom__".equals(value)) {
            return StepResult.advance(null, null);
        }
        if (existing != null && !"__custom__".equals(existing.toString())) {
            return StepResult.advance(null, null);
        }
        String resolvedPrompt = WorkflowTemplateInterpolator.interpolate(prompt, state, templatePolicy);
        OutboundResponse richReply = buildRichReply(event, state, resolvedPrompt);
        if (richReply != null) {
            return StepResult.waiting(resolvedPrompt, storeIn, richReply);
        }
        return StepResult.waiting(resolvedPrompt, storeIn);
    }

    private OutboundResponse buildRichReply(Event event, ConfigurableWorkflowState state, String resolvedPrompt) {
        if ("plain_text".equalsIgnoreCase(controlMode)) {
            return null;
        }
        String text = resolvedPrompt != null ? resolvedPrompt : prompt;
        if (choiceProvider != null) {
            try {
                List<ResponseIntent.Choice> dynamic = choiceProvider.getChoices(event, state);
                if (dynamic != null && !dynamic.isEmpty()) {
                    List<ResponseIntent.Choice> merged = new ArrayList<>(dynamic);
                    for (Map<String, Object> m : choices) {
                        merged.add(new ResponseIntent.Choice(
                                stringVal(m, "id"),
                                stringVal(m, "label"),
                                stringVal(m, "description")));
                    }
                    return OutboundResponse.of(text, new PresentChoices(text, merged));
                }
            } catch (Exception ignored) {
                // Fall back to static choices or text-only
            }
        }
        if ("present_choices".equals(intent) && !choices.isEmpty()) {
            List<ResponseIntent.Choice> cs = choices.stream()
                    .map(m -> new ResponseIntent.Choice(
                            stringVal(m, "id"),
                            stringVal(m, "label"),
                            stringVal(m, "description")))
                    .collect(Collectors.toList());
            return OutboundResponse.of(text, new PresentChoices(text, cs));
        }
        if ("confirm_action".equals(intent)) {
            return OutboundResponse.of(text, new ConfirmAction(
                    text,
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
