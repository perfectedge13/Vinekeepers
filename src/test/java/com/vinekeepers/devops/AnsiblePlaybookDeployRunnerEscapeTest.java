package com.vinekeepers.devops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AnsiblePlaybookDeployRunnerEscapeTest {

    @Test
    void escapeExtraVarValue() {
        assertFalse(AnsiblePlaybookDeployRunner.escapeExtraVarValue("plain").startsWith("\""));
        assertEquals("\"a\\\"b\"", AnsiblePlaybookDeployRunner.escapeExtraVarValue("a\"b"));
    }
}
