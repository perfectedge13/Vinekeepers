package com.vinekeepers.workflow;

import com.vinekeepers.workflow.GatheringState.Step;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GatheringStateTest {

    @Test
    void initialReturnsAwaitingProjectWithNullProjectAndChange() {
        GatheringState state = GatheringState.initial();
        assertNotNull(state);
        assertEquals(Step.AWAITING_PROJECT, state.getStep());
        assertNull(state.getProject());
        assertNull(state.getCodeChangeDescription());
    }

    @Test
    void withProjectAdvancesToAwaitingChange() {
        GatheringState state = GatheringState.initial().withProject("my-project");
        assertEquals(Step.AWAITING_CHANGE, state.getStep());
        assertEquals("my-project", state.getProject());
        assertNull(state.getCodeChangeDescription());
    }

    @Test
    void withCodeChangeAdvancesToReadyToRun() {
        GatheringState state = GatheringState.initial()
                .withProject("p")
                .withCodeChange("add feature X");
        assertEquals(Step.READY_TO_RUN, state.getStep());
        assertEquals("p", state.getProject());
        assertEquals("add feature X", state.getCodeChangeDescription());
    }

    @Test
    void withStepUpdatesStepOnly() {
        GatheringState state = new GatheringState(Step.AWAITING_CHANGE, "proj", null)
                .withStep(Step.DONE);
        assertEquals(Step.DONE, state.getStep());
        assertEquals("proj", state.getProject());
        assertNull(state.getCodeChangeDescription());
    }

    @Test
    void constructorNormalizesNullStepToAwaitingProject() {
        GatheringState state = new GatheringState(null, "p", "c");
        assertEquals(Step.AWAITING_PROJECT, state.getStep());
    }
}
