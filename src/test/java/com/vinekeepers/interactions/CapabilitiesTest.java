package com.vinekeepers.interactions;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilitiesTest {

    @Test
    void supportsIntentReturnsTrueForSupportedType() {
        Capabilities caps = new Capabilities(
                Set.of(ResponseIntentType.PRESENT_CHOICES, ResponseIntentType.CONFIRM_ACTION),
                true, true, true, false, 3000, 25, 5, 5);
        assertTrue(caps.supportsIntent(new PresentChoices("Pick", java.util.List.of())));
        assertTrue(caps.supportsIntent(new ConfirmAction("OK?", "Yes", "No")));
    }

    @Test
    void supportsIntentReturnsFalseForUnsupportedType() {
        Capabilities caps = new Capabilities(
                Set.of(ResponseIntentType.PRESENT_CHOICES),
                true, true, true, false, 3000, 25, 5, 5);
        assertFalse(caps.supportsIntent(new ConfirmAction("OK?", "Yes", "No")));
    }

    @Test
    void supportsIntentReturnsFalseForNull() {
        Capabilities caps = new Capabilities(
                Set.of(ResponseIntentType.PRESENT_CHOICES),
                true, true, true, false, 3000, 25, 5, 5);
        assertFalse(caps.supportsIntent(null));
    }
}
