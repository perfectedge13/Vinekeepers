package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.LifecycleContextStore;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
    }
}
