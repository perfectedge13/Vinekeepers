package com.vinekeepers.workflow;

import com.vinekeepers.interactions.OutboundResponse;
import com.vinekeepers.interactions.PresentChoices;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowRunResultTest {

    @Test
    void continueWithoutReplyHasNoRichReply() {
        WorkflowRunResult r = WorkflowRunResult.continueWithoutReply();
        assertTrue(r.getRichReply().isEmpty());
        assertFalse(r.isWaiting());
        assertFalse(r.isCompleted());
    }

    @Test
    void waitingWithMessageOnlyHasNoRichReply() {
        WorkflowRunResult r = WorkflowRunResult.waiting("Prompt", "field");
        assertTrue(r.getRichReply().isEmpty());
        assertEquals("Prompt", r.getReplyMessage());
        assertTrue(r.isWaiting());
        assertEquals("field", r.getWaitingForField());
    }

    @Test
    void waitingWithRichReplyHasRichReply() {
        OutboundResponse rich = OutboundResponse.ofIntent(new PresentChoices("Choose", List.of()));
        WorkflowRunResult r = WorkflowRunResult.waiting(rich, "field");
        assertTrue(r.getRichReply().isPresent());
        assertEquals(rich, r.getRichReply().get());
        assertTrue(r.isWaiting());
        assertEquals("field", r.getWaitingForField());
    }

    @Test
    void completedWithMessageOnlyHasNoRichReply() {
        WorkflowRunResult r = WorkflowRunResult.completed("Done.");
        assertTrue(r.getRichReply().isEmpty());
        assertEquals("Done.", r.getReplyMessage());
        assertTrue(r.isCompleted());
    }

    @Test
    void completedWithRichReplyHasRichReply() {
        OutboundResponse rich = OutboundResponse.of("Done.", new PresentChoices("Choose", List.of()));
        WorkflowRunResult r = WorkflowRunResult.completed(rich);
        assertTrue(r.getRichReply().isPresent());
        assertEquals(rich, r.getRichReply().get());
        assertTrue(r.isCompleted());
    }

    @Test
    void errorHasNoRichReply() {
        WorkflowRunResult r = WorkflowRunResult.error("failed");
        assertTrue(r.getRichReply().isEmpty());
        assertEquals("failed", r.getErrorMessage());
    }
}
