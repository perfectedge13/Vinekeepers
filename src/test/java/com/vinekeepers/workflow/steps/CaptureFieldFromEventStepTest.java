package com.vinekeepers.workflow.steps;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaptureFieldFromEventStepTest {

    @Test
    void interactionWithNestedValuesMapCapturesSelectedValue() {
        // Discord gateway sends payload.values = Map.of("values", List.of("owner/repo"))
        Map<String, Object> payload = Map.of(
                "values", Map.of("values", List.of("owner/repo")),
                "customId", "repo_select"
        );
        Event event = new Event("discord:1", "interaction", payload);
        CaptureFieldFromEventStep step = new CaptureFieldFromEventStep("project", null);
        StepResult result = step.execute(event, new ConfigurableWorkflowState(), 1);
        assertEquals("project", result.getStoreIn());
        assertEquals("owner/repo", result.getStoreValue());
    }

    @Test
    void interactionWithDirectValuesListCapturesJoined() {
        Map<String, Object> payload = Map.of("values", List.of("a", "b"));
        Event event = new Event("discord:1", "interaction", payload);
        CaptureFieldFromEventStep step = new CaptureFieldFromEventStep("project", null);
        StepResult result = step.execute(event, new ConfigurableWorkflowState(), 1);
        assertEquals("a, b", result.getStoreValue());
    }

    @Test
    void interactionWithNoValuesFallsBackToCustomId() {
        Map<String, Object> payload = Map.of("customId", "button_launch");
        Event event = new Event("discord:1", "interaction", payload);
        CaptureFieldFromEventStep step = new CaptureFieldFromEventStep("confirmAction", null);
        StepResult result = step.execute(event, new ConfigurableWorkflowState(), 1);
        assertEquals("button_launch", result.getStoreValue());
    }

    @Test
    void messageEventUsesContent() {
        Event event = new Event("discord:1", "message", Map.of("content", "novawilde/Vinekeepers"));
        CaptureFieldFromEventStep step = new CaptureFieldFromEventStep("project", null);
        StepResult result = step.execute(event, new ConfigurableWorkflowState(), 1);
        assertEquals("novawilde/Vinekeepers", result.getStoreValue());
    }

    @Test
    void messageEventWithTrimAndLowerTrimsAndLowercasesContent() {
        Event event = new Event("discord:1", "message", Map.of("content", "  Arrietty Room  "));
        CaptureFieldFromEventStep step = new CaptureFieldFromEventStep("room", null, true);
        StepResult result = step.execute(event, new ConfigurableWorkflowState(), 1);
        assertEquals("arrietty room", result.getStoreValue());
    }

    @Test
    void trimAndLowerFalseLeavesContentUnchanged() {
        Event event = new Event("discord:1", "message", Map.of("content", "  Mixed CASE  "));
        CaptureFieldFromEventStep step = new CaptureFieldFromEventStep("field", null, false);
        StepResult result = step.execute(event, new ConfigurableWorkflowState(), 1);
        assertEquals("  Mixed CASE  ", result.getStoreValue());
    }

    @Test
    void twoArgConstructorDefaultsTrimAndLowerToFalse() {
        Event event = new Event("discord:1", "message", Map.of("content", "  Unchanged  "));
        CaptureFieldFromEventStep step = new CaptureFieldFromEventStep("field", null);
        StepResult result = step.execute(event, new ConfigurableWorkflowState(), 1);
        assertEquals("  Unchanged  ", result.getStoreValue());
    }

    @Test
    void transformOrderTrimThenLowerApplied() {
        Event event = new Event("discord:1", "message", Map.of("content", "  Mixed CASE  "));
        CaptureFieldFromEventStep step = new CaptureFieldFromEventStep("field", null, false,
                List.of("trim", "lower"), null);
        StepResult result = step.execute(event, new ConfigurableWorkflowState(), 1);
        assertEquals("mixed case", result.getStoreValue());
    }

    @Test
    void trimAndLowerWinsWhenBothTrimAndLowerAndTransformsPresent() {
        Event event = new Event("discord:1", "message", Map.of("content", "  UPPER  "));
        CaptureFieldFromEventStep step = new CaptureFieldFromEventStep("field", null, true,
                List.of("upper"), null);
        StepResult result = step.execute(event, new ConfigurableWorkflowState(), 1);
        assertEquals("upper", result.getStoreValue());
    }

    @Test
    void trimAndLowerBackwardCompatSameAsTrimAndLowerTransforms() {
        Event event = new Event("discord:1", "message", Map.of("content", "  Same  "));
        CaptureFieldFromEventStep withFlag = new CaptureFieldFromEventStep("a", null, true);
        CaptureFieldFromEventStep withTransforms = new CaptureFieldFromEventStep("a", null, false,
                List.of("trim", "lower"), null);
        assertEquals(withFlag.execute(event, new ConfigurableWorkflowState(), 1).getStoreValue(),
                withTransforms.execute(event, new ConfigurableWorkflowState(), 1).getStoreValue());
    }

    @Test
    void transformsWithDefaultUsesDefaultWhenContentBlank() {
        Event event = new Event("discord:1", "message", Map.of("content", "   "));
        CaptureFieldFromEventStep step = new CaptureFieldFromEventStep("field", null, false,
                List.of("trim", "default"), "fallback");
        StepResult result = step.execute(event, new ConfigurableWorkflowState(), 1);
        assertEquals("fallback", result.getStoreValue());
    }
}
