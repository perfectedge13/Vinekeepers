package com.vinekeepers.workflow.steps;

import com.vinekeepers.bot.NormalizedEventContext;
import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepResult;
import com.vinekeepers.workflow.WorkflowTransforms;
import com.vinekeepers.workflow.WorkflowStep;

import java.util.List;

/**
 * Conversational step that captures a field from the current event after a previous prompt paused the run.
 * Supports transforms list; when trimAndLower is true it overrides transforms (backward compatibility).
 */
public final class CaptureFieldFromEventStep implements WorkflowStep {

    private final String storeIn;
    private final String contentKey;
    private final boolean trimAndLower;
    private final List<String> transforms;
    private final String defaultValue;

    public CaptureFieldFromEventStep(String storeIn, String contentKey) {
        this(storeIn, contentKey, false, null, null);
    }

    public CaptureFieldFromEventStep(String storeIn, String contentKey, boolean trimAndLower) {
        this(storeIn, contentKey, trimAndLower, null, null);
    }

    public CaptureFieldFromEventStep(String storeIn, String contentKey, boolean trimAndLower,
                                     List<String> transforms, String defaultValue) {
        this.storeIn = storeIn != null ? storeIn : "input";
        this.contentKey = contentKey;
        this.trimAndLower = trimAndLower;
        this.transforms = transforms != null ? List.copyOf(transforms) : List.of();
        this.defaultValue = defaultValue;
    }

    @Override
    public StepResult execute(Event event, ConfigurableWorkflowState state, int stepIndex) {
        String content = readContent(event);
        List<String> effectiveTransforms = effectiveTransforms();
        String defaultVal = defaultValue != null ? defaultValue : "";
        content = WorkflowTransforms.applyAll(content, effectiveTransforms, defaultVal);
        if (content == null) content = defaultVal;
        return StepResult.advance(storeIn, content);
    }

    /**
     * When both trimAndLower and transforms are present, trimAndLower wins (backward compatibility).
     */
    private List<String> effectiveTransforms() {
        if (trimAndLower) return List.of("trim", "lower");
        return transforms;
    }

    private String readContent(Event event) {
        if (event == null) {
            return "";
        }
        if ("interaction".equals(event.getKind())) {
            List<String> interactionValues = NormalizedEventContext.getInteractionValuesFromPayload(event.getPayload());
            if (!interactionValues.isEmpty()) {
                return String.join(", ", interactionValues);
            }
            String customId = event.getPayload("customId", String.class);
            if (customId != null && !customId.isBlank()) {
                return customId;
            }
        }
        if (contentKey != null && !contentKey.isBlank()) {
            String explicit = event.getPayload(contentKey, String.class);
            if (explicit != null) {
                return explicit;
            }
        }
        String content = event.getPayload("content", String.class);
        if (content == null) {
            content = event.getPayload("text", String.class);
        }
        return content != null ? content : "";
    }
}
