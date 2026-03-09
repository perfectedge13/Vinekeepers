package com.vinekeepers.interactions;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboundResponseTest {

    @Test
    void ofTextReturnsTextOnly() {
        OutboundResponse r = OutboundResponse.ofText("hello");
        assertEquals(Optional.of("hello"), r.getText());
        assertTrue(r.getIntent().isEmpty());
    }

    @Test
    void ofIntentReturnsIntentOnly() {
        ResponseIntent intent = new PresentChoices("Choose", java.util.List.of());
        OutboundResponse r = OutboundResponse.ofIntent(intent);
        assertTrue(r.getText().isEmpty());
        assertEquals(Optional.of(intent), r.getIntent());
    }

    @Test
    void ofWithBothStoresTextAndIntent() {
        ResponseIntent intent = new ConfirmAction("Sure?", "Yes", "No");
        OutboundResponse r = OutboundResponse.of("message", intent);
        assertEquals(Optional.of("message"), r.getText());
        assertEquals(Optional.of(intent), r.getIntent());
    }

    @Test
    void ofWithNullTextUsesEmpty() {
        ResponseIntent intent = new PresentChoices("Pick", java.util.List.of());
        OutboundResponse r = OutboundResponse.of(null, intent);
        assertTrue(r.getText().isEmpty());
        assertEquals(Optional.of(intent), r.getIntent());
    }

    @Test
    void constructorRejectsBothNullAndBlank() {
        assertThrows(IllegalArgumentException.class, () -> new OutboundResponse(null, null));
        assertThrows(IllegalArgumentException.class, () -> new OutboundResponse("", null));
        assertThrows(IllegalArgumentException.class, () -> new OutboundResponse("   ", null));
    }

    @Test
    void ofTextRejectsNull() {
        assertThrows(NullPointerException.class, () -> OutboundResponse.ofText(null));
    }

    @Test
    void ofIntentRejectsNull() {
        assertThrows(NullPointerException.class, () -> OutboundResponse.ofIntent(null));
    }
}
