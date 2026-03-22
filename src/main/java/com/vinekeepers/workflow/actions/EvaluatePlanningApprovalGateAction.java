package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.PlanReadinessStatus;
import com.vinekeepers.workflow.deliberation.DeliberationEngine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sets {@code planningReadyForApproval} when the posted packet, depth, and critique readiness align.
 * Dual-writes {@code approvalReady} / {@code planningApprovalReady} and refreshes {@code reviewReady} for the posted
 * packet view (weaker than approval).
 */
public final class EvaluatePlanningApprovalGateAction implements com.vinekeepers.workflow.WorkflowAction {

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("planningReadyForApproval", "false");
        spread.put("planningApprovalReady", "false");
        spread.put("approvalReady", "false");
        spread.put("planningApprovalGateReason", "");
        if (state == null) {
            spread.put("planningApprovalGateReason", "No workflow state is loaded yet, so we cannot check approval readiness.");
            return spread;
        }
        boolean posted = parseInt(getString(state, "planningPacketPostedVersion"), 0) > 0;
        boolean depthOk = "true".equalsIgnoreCase(String.valueOf(state.get("planningPacketDepthOk")));
        String readiness = getString(state, "planReadinessStatus");
        boolean ready = PlanReadinessStatus.READY.equals(readiness);
        boolean humanOk = "true".equalsIgnoreCase(String.valueOf(state.get("humanDiscoveryCompleted")));
        boolean noPendingClarification = !"true".equalsIgnoreCase(String.valueOf(state.get("planningUserInputRequired")));

        boolean reviewSignal = posted && depthOk && noPendingClarification;
        spread.put("planningReviewReady", reviewSignal ? "true" : "false");
        spread.put("reviewReady", reviewSignal ? "true" : "false");
        if (reviewSignal) {
            spread.put(
                    "planningReviewReadyReason",
                    "The planning packet is posted, depth checks passed, and no clarification is pending — ready for human review.");
            spread.put("reviewReadyReason", spread.get("planningReviewReadyReason"));
        } else {
            StringBuilder rr = new StringBuilder();
            if (!posted) {
                rr.append("The full packet has not been posted to the thread yet. ");
            }
            if (!depthOk) {
                rr.append("Depth checks are not satisfied for the current draft. ");
            }
            if (!noPendingClarification) {
                rr.append("A clarification is still open. ");
            }
            String rrs = rr.toString().trim();
            spread.put("planningReviewReadyReason", rrs.isEmpty() ? "Review readiness could not be confirmed." : rrs);
            spread.put("reviewReadyReason", spread.get("planningReviewReadyReason"));
        }

        StringBuilder reason = new StringBuilder();
        if (!posted) {
            reason.append("Post the planning packet to this thread first — approval is only offered after you can read the full draft. ");
        }
        if (!depthOk) {
            reason.append("The packet still fails the depth check — tighten exploration or architecture notes before launch approval. ");
        }
        if (!ready) {
            reason.append(
                    "Critique readiness is not READY yet (current status: "
                            + (readiness != null ? readiness : "unknown")
                            + "). ");
        }
        if (!humanOk) {
            reason.append("Discovery in the coordinator flow is not marked complete. ");
        }
        if (!noPendingClarification) {
            reason.append("Finish or merge the open clarification before approving launch. ");
        }
        boolean ok = posted && depthOk && ready && humanOk && noPendingClarification;
        spread.put("planningReadyForApproval", ok ? "true" : "false");
        spread.put("planningApprovalReady", ok ? "true" : "false");
        spread.put("approvalReady", ok ? "true" : "false");
        spread.put(
                "planningApprovalGateReason",
                ok
                        ? "All launch-approval gates are satisfied — you can approve when you are comfortable."
                        : reason.toString().trim());
        spread.put("planningApprovalReadyReason", spread.get("planningApprovalGateReason"));
        spread.put("approvalReadyReason", spread.get("planningApprovalGateReason"));
        if (state.get("planningPhase") != null) {
            spread.put("planningPhase", state.get("planningPhase").toString());
        }
        DeliberationEngine.applyDerivedDeliberationSpread(spread);
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
