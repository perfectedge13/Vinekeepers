package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.PlanReadinessStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sets {@code planningReadyForApproval} when the posted packet, depth, and critique readiness align.
 * Approval prompts should branch on this key so users do not see approval when the packet is not materially ready.
 */
public final class EvaluatePlanningApprovalGateAction implements com.vinekeepers.workflow.WorkflowAction {

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("planningReadyForApproval", "false");
        spread.put("planningApprovalGateReason", "");
        if (state == null) {
            spread.put("planningApprovalGateReason", "No workflow state.");
            return spread;
        }
        boolean posted = parseInt(getString(state, "planningPacketPostedVersion"), 0) > 0;
        boolean depthOk = "true".equalsIgnoreCase(String.valueOf(state.get("planningPacketDepthOk")));
        String readiness = getString(state, "planReadinessStatus");
        boolean ready = PlanReadinessStatus.READY.equals(readiness);
        boolean humanOk = "true".equalsIgnoreCase(String.valueOf(state.get("humanDiscoveryCompleted")));
        boolean noPendingClarification = !"true".equalsIgnoreCase(String.valueOf(state.get("planningUserInputRequired")));

        StringBuilder reason = new StringBuilder();
        if (!posted) {
            reason.append("Planning packet has not been posted yet. ");
        }
        if (!depthOk) {
            reason.append("Packet depth check not satisfied. ");
        }
        if (!ready) {
            reason.append("Readiness is not READY (").append(readiness != null ? readiness : "?").append("). ");
        }
        if (!humanOk) {
            reason.append("Coordinator discovery not marked complete. ");
        }
        if (!noPendingClarification) {
            reason.append("Clarification still pending. ");
        }
        boolean ok = posted && depthOk && ready && humanOk && noPendingClarification;
        spread.put("planningReadyForApproval", ok ? "true" : "false");
        spread.put("planningApprovalGateReason", ok ? "All approval gates satisfied." : reason.toString().trim());
        return spread;
    }

    private static int parseInt(String s, int dflt) {
        if (s == null || s.isBlank()) {
            return dflt;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    private static String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
