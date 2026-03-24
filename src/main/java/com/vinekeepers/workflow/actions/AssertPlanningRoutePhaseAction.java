package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.WorkflowAction;
import com.vinekeepers.workflow.planning.PlanningNextAction;
import com.vinekeepers.workflow.planning.PlanningRoutingBridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fails the graph step (runtime exception) when entering packet or blocked phase with inconsistent route snapshot.
 */
public final class AssertPlanningRoutePhaseAction implements WorkflowAction {

    private static final Logger log = LoggerFactory.getLogger(AssertPlanningRoutePhaseAction.class);

    public static final String BIND_EXPECTED_PHASE = "expectedPhase";

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        String expected = string(bind != null ? bind.get(BIND_EXPECTED_PHASE) : null);
        String phase = string(state != null ? state.get(PlanningRoutingBridge.NEXT_PHASE_KEY) : null);
        String action = string(state != null ? state.get(PlanningRoutingBridge.NEXT_ACTION_KEY) : null);

        if (PlanningRoutingBridge.PHASE_PLANNING_PACKET.equals(expected)) {
            if (!PlanningRoutingBridge.PHASE_PLANNING_PACKET.equals(phase)
                    || !PlanningNextAction.READY_FOR_PACKET.name().equals(action)) {
                log.error(
                        "Planning route assertion failed: expected packet phase with READY_FOR_PACKET; planningNextPhase={} planningNextAction={}",
                        phase,
                        action);
                throw new IllegalStateException(
                        "Planning route assertion failed: expected planning_packet + READY_FOR_PACKET, got phase="
                                + phase
                                + " action="
                                + action);
            }
        } else if (PlanningRoutingBridge.PHASE_PLANNING_BLOCKED.equals(expected)) {
            if (!PlanningRoutingBridge.PHASE_PLANNING_BLOCKED.equals(phase)
                    || !PlanningNextAction.BLOCKED.name().equals(action)) {
                log.error(
                        "Planning route assertion failed: expected blocked phase with BLOCKED; planningNextPhase={} planningNextAction={}",
                        phase,
                        action);
                throw new IllegalStateException(
                        "Planning route assertion failed: expected planning_blocked + BLOCKED, got phase="
                                + phase
                                + " action="
                                + action);
            }
        } else {
            log.warn("assert_planning_route_phase: unknown expectedPhase bind value: {}", expected);
        }
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("planningRoutePhaseAssertionOk", "true");
        return spread;
    }

    private static String string(Object raw) {
        return raw != null ? raw.toString().trim() : "";
    }
}
