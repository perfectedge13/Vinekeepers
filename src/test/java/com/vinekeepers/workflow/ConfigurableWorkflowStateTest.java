package com.vinekeepers.workflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurableWorkflowStateTest {

    @Test
    void defaultStepIndexIsZero() {
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        assertEquals(0, state.getStepIndex());
        assertTrue(state.getData().isEmpty());
    }

    @Test
    void initialStepIndexConstructor() {
        ConfigurableWorkflowState state = new ConfigurableWorkflowState(3);
        assertEquals(3, state.getStepIndex());
    }

    @Test
    void putAndGet() {
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        state.put("name", "Luna");
        assertTrue(state.has("name"));
        assertEquals("Luna", state.get("name"));
        assertEquals("Luna", state.get("name", String.class));
    }

    @Test
    void getWithTypeReturnsNullForWrongType() {
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        state.put("n", 42);
        assertEquals(42, state.get("n"));
        assertNull(state.get("n", String.class));
        assertEquals(42, state.get("n", Integer.class));
    }

    @Test
    void setStepIndex() {
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        state.setStepIndex(5);
        assertEquals(5, state.getStepIndex());
    }

    @Test
    void putIgnoresNullKey() {
        ConfigurableWorkflowState state = new ConfigurableWorkflowState();
        state.put(null, "v");
        assertTrue(state.getData().isEmpty());
    }
}
