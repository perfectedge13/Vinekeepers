package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.WorkflowAction;
import com.vinekeepers.workflow.planning.PlanningRoutingBridge;
import com.vinekeepers.workflow.planreview.PlanningUserFacingCopy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles {@code planning_routing_invariant} when post-evaluation rules did not match a known {@code planningNextPhase}.
 * Logs loudly and sets cycle error keys; does not post user-facing blocked-thread copy.
 */
public final class PlanningRouteInvariantFailedAction implements WorkflowAction {

    private static final Logger log = LoggerFactory.getLogger(PlanningRouteInvariantFailedAction.class);

    public static final String MACHINE_CODE = "PLANNING_ROUTE_UNRESOLVED";

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        String phase = string(state != null ? state.get(PlanningRoutingBridge.NEXT_PHASE_KEY) : null);
        String action = string(state != null ? state.get(PlanningRoutingBridge.NEXT_ACTION_KEY) : null);
        String cycleErr = string(state != null ? state.get("planningRoomCycleError") : null);
        log.error(
                "Planning graph routing invariant: no rs_evaluation_branch match after clarification. planningNextPhase={} planningNextAction={} planningRoomCycleError={}",
                phase,
                action,
                cycleErr);
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("planningRoomCycleError", MACHINE_CODE);
        spread.put(
                "planningRoomCycleErrorUserMessage",
                PlanningUserFacingCopy.humanizePlanningRoomCycleErrorCode(MACHINE_CODE));
        spread.put("planningRouteInvariantTriggered", "true");
        return spread;
    }

    private static String string(Object raw) {
        return raw != null ? raw.toString().trim() : "";
    }
}
