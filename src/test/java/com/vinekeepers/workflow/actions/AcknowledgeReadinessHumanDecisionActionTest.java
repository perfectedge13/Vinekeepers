package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.planning.PlanningReadinessSpread;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AcknowledgeReadinessHumanDecisionActionTest {

    @Test
    void setsHumanAcknowledgedFlagWithoutRewritingReadiness() {
        AcknowledgeReadinessHumanDecisionAction action =
                new AcknowledgeReadinessHumanDecisionAction(null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> spread =
                (Map<String, Object>) action.run(new Event("t", "k", Map.of()), Map.of(), Map.of());
        assertEquals("true", spread.get(PlanningReadinessSpread.HUMAN_READINESS_ACKNOWLEDGED_KEY));
    }
}
