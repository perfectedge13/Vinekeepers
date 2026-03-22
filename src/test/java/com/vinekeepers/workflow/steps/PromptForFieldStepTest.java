package com.vinekeepers.workflow.steps;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.StepOutcome;
import com.vinekeepers.workflow.StepResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptForFieldStepTest {

    @Test
    void plainTextControlSkipsRichReplyDespitePresentChoicesIntent() {
        PromptForFieldStep step =
                new PromptForFieldStep(
                        "Pick",
                        "choice",
                        "present_choices",
                        List.of(
                                Map.of(
                                        "id",
                                        "a",
                                        "label",
                                        "A",
                                        "description",
                                        "")),
                        null,
                        null,
                        List.of(),
                        null,
                        "plain_text");
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        StepResult r = step.execute(new Event("x", "message", Map.of()), state, 0);
        assertEquals(StepOutcome.WAITING, r.getOutcome());
        assertFalse(r.getRichReply().isPresent());
    }

    @Test
    void presentChoicesIntentStillBuildsRichReplyWhenNoPlainTextControl() {
        PromptForFieldStep step =
                new PromptForFieldStep(
                        "Pick",
                        "choice",
                        "present_choices",
                        List.of(Map.of("id", "a", "label", "A", "description", "")),
                        null,
                        null,
                        List.of(),
                        null,
                        null);
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        StepResult r = step.execute(new Event("x", "message", Map.of()), state, 0);
        assertTrue(r.getRichReply().isPresent());
    }
}
