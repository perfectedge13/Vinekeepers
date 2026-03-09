package com.vinekeepers.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepResultTest {

    @Test
    void advanceSetsStoreInAndStoreValueNotDone() {
        StepResult r = StepResult.advance("key", "value");
        assertNull(r.getNextStepIndex());
        assertEquals("key", r.getStoreIn());
        assertEquals("value", r.getStoreValue());
        assertFalse(r.isDone());
        assertEquals("", r.getMessage());
    }

    @Test
    void goToSetsNextStepIndex() {
        StepResult r = StepResult.goTo(2);
        assertEquals(2, r.getNextStepIndex());
        assertNull(r.getStoreIn());
        assertNull(r.getStoreValue());
        assertFalse(r.isDone());
    }

    @Test
    void goToWithStoreSetsAll() {
        StepResult r = StepResult.goTo(1, "x", 42);
        assertEquals(1, r.getNextStepIndex());
        assertEquals("x", r.getStoreIn());
        assertEquals(42, r.getStoreValue());
        assertFalse(r.isDone());
    }

    @Test
    void doneSetsMessageAndDone() {
        StepResult r = StepResult.done("Bye.");
        assertNull(r.getNextStepIndex());
        assertNull(r.getStoreIn());
        assertNull(r.getStoreValue());
        assertTrue(r.isDone());
        assertEquals("Bye.", r.getMessage());
    }

    @Test
    void constructorWithNullMessageUsesEmptyString() {
        StepResult r = new StepResult(null, null, null, false, null);
        assertEquals("", r.getMessage());
    }

    @Test
    void waitingWithRichReplyStoresRichReply() {
        com.vinekeepers.interactions.OutboundResponse rich =
                com.vinekeepers.interactions.OutboundResponse.ofText("Pick one");
        StepResult r = StepResult.waiting("Prompt", "field", rich);
        assertTrue(r.getRichReply().isPresent());
        assertEquals(rich, r.getRichReply().get());
        assertEquals("field", r.getWaitingForField());
        assertEquals(StepOutcome.WAITING, r.getOutcome());
    }

    @Test
    void waitingWithoutRichReplyHasEmptyRichReply() {
        StepResult r = StepResult.waiting("Prompt", "field");
        assertTrue(r.getRichReply().isEmpty());
    }

    @Test
    void goToWithClearKeysReturnsClearKeys() {
        StepResult r = StepResult.goTo(2, List.of("project", "repo"));
        assertEquals(2, r.getNextStepIndex());
        assertEquals(List.of("project", "repo"), r.getClearKeys());
    }

    @Test
    void goToWithoutClearKeysHasEmptyClearKeys() {
        StepResult r = StepResult.goTo(1);
        assertTrue(r.getClearKeys().isEmpty());
    }
}
