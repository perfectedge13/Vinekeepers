package com.vinekeepers.workflow.planning;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanningCyclePipelineMaxInnerRoundsTest {

    @Test
    void silentSynthesisUsesSingleInnerRoundBeforeEvaluation() throws Exception {
        Field f = PlanningCyclePipeline.class.getDeclaredField("MAX_BOT_INNER_ROUNDS");
        f.setAccessible(true);
        assertEquals(1, f.getInt(null));
    }
}
