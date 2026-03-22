package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvaluatePlanningApprovalGateActionTest {

    @Test
    void gateOpenWhenAllSignalsTrue() {
        Map<String, Object> state = new HashMap<>();
        state.put("planningPacketPostedVersion", "1");
        state.put("planningPacketDepthOk", "true");
        state.put("planReadinessStatus", "READY");
        state.put("humanDiscoveryCompleted", "true");
        state.put("planningUserInputRequired", "false");
        @SuppressWarnings("unchecked")
        Map<String, Object> spread = (Map<String, Object>) new EvaluatePlanningApprovalGateAction()
                .run(new Event("x", "m", Map.of()), state, Map.of());
        assertEquals("true", spread.get("planningReadyForApproval"));
        assertEquals("true", spread.get("approvalReady"));
        assertEquals("true", spread.get("planningReviewReady"));
    }

    @Test
    void gateClosedWhenDepthFails() {
        Map<String, Object> state = new HashMap<>();
        state.put("planningPacketPostedVersion", "1");
        state.put("planningPacketDepthOk", "false");
        state.put("planReadinessStatus", "READY");
        state.put("humanDiscoveryCompleted", "true");
        state.put("planningUserInputRequired", "false");
        @SuppressWarnings("unchecked")
        Map<String, Object> spread = (Map<String, Object>) new EvaluatePlanningApprovalGateAction()
                .run(new Event("x", "m", Map.of()), state, Map.of());
        assertEquals("false", spread.get("planningReadyForApproval"));
        assertEquals("false", spread.get("planningReviewReady"));
    }
}
