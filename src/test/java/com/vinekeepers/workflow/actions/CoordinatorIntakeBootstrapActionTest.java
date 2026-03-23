package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.LifecycleContextStore;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoordinatorIntakeBootstrapActionTest {

    @Test
    void missingPlan_returnsErrorSpread() {
        OutboundDeliveryRouter router = new OutboundDeliveryRouter(new LifecycleContextStore());
        CoordinatorIntakeBootstrapAction action = new CoordinatorIntakeBootstrapAction(router, new FeaturePlanStateStore());
        @SuppressWarnings("unchecked")
        Map<String, Object> spread =
                (Map<String, Object>)
                        action.run(
                                new Event("discord:g:1", "message", Map.of()),
                                Map.of("contextId", "missing"),
                                Map.of());
        assertTrue(String.valueOf(spread.get("intakeKickoffPostedError")).contains("No plan"));
        assertFalse(Boolean.parseBoolean(String.valueOf(spread.get(CoordinatorIntakeBootstrapAction.KICKOFF_VISIBLE_OUTCOME_KEY))));
    }

    @Test
    void kickoffBody_hasNoOptionalSolicitationBeat() throws Exception {
        Method m = CoordinatorIntakeBootstrapAction.class.getDeclaredMethod("buildKickoffBody", String.class, String.class);
        m.setAccessible(true);
        String body = (String) m.invoke(null, "org/r", "Implement feature X");
        String lower = body.toLowerCase();
        assertFalse(lower.contains("must-haves"));
        assertFalse(lower.contains("add scope"));
        assertTrue(lower.contains("continuing to draft") || lower.contains("draft"));
    }
}
