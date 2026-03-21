package com.vinekeepers.gadget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GadgetDeployRunnerEscapeTest {

    @Test
    void escapeWrapsValueWithQuotesWhenNeeded() {
        assertFalse(GadgetDeployRunner.escapeExtraVarValue("plain").startsWith("\""));
        assertEquals("\"a\\\"b\"", GadgetDeployRunner.escapeExtraVarValue("a\"b"));
    }
}
