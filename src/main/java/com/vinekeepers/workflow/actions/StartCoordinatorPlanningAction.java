package com.vinekeepers.workflow.actions;

import com.vinekeepers.bot.NormalizedEventContext;
import com.vinekeepers.core.VinekeepersEngine;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeatureRoomStateStore;

import java.util.HashMap;
import java.util.Map;

/**
 * After Luna posts the intake/spec handoff, explicitly runs the coordinator bot's thread workflow once
 * using an in-process synthetic Discord message (same thread id as channelId + threadId). Does not post a
 * user-visible dummy message to Discord.
 */
public final class StartCoordinatorPlanningAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final String DEFAULT_COORDINATOR = "arrietty";

    private final VinekeepersEngine engine;
    private final FeatureRoomStateStore featureRoomStateStore;

    public StartCoordinatorPlanningAction(VinekeepersEngine engine, FeatureRoomStateStore featureRoomStateStore) {
        this.engine = engine;
        this.featureRoomStateStore = featureRoomStateStore;
    }

    @Override
    public Object run(Event parentEvent, Map<String, Object> state, Map<String, Object> bind) {
        if (engine == null) {
            return "start_coordinator_planning: engine not available.";
        }
        String intakeThreadId = firstNonBlank(getString(bind, "intakeThreadId"), getString(state, "intakeThreadId"));
        if (intakeThreadId == null || intakeThreadId.isBlank()) {
            return "start_coordinator_planning: missing intakeThreadId.";
        }
        if (CreateThreadAction.THREAD_CREATE_FAILED.equals(intakeThreadId)) {
            return "start_coordinator_planning: intake thread was not created.";
        }
        String coordinator = firstNonBlank(getString(bind, "coordinatorBotId"), getString(state, "coordinatorBotId"));
        if (coordinator == null || coordinator.isBlank()) {
            coordinator = featureRoomStateStore != null
                    ? featureRoomStateStore.getByIntakeThreadId(intakeThreadId)
                    .flatMap(FeatureRoomStateStore::resolveCoordinatorConfiguredBotId)
                    .orElse(DEFAULT_COORDINATOR)
                    : DEFAULT_COORDINATOR;
        }
        Event synthetic = buildSyntheticThreadMessage(parentEvent, intakeThreadId.trim());
        return engine.dispatchCoordinatorPlanningKickoff(synthetic, coordinator);
    }

    private static Event buildSyntheticThreadMessage(Event parentEvent, String intakeThreadId) {
        String sourceId = parentEvent != null ? parentEvent.getSourceId() : "discord:synthetic";
        NormalizedEventContext ctx = NormalizedEventContext.from(parentEvent);
        String authorId = ctx.getActorId() != null ? ctx.getActorId() : "";
        Map<String, Object> payload = new HashMap<>();
        payload.put("channelId", intakeThreadId);
        payload.put("threadId", intakeThreadId);
        if (!authorId.isBlank()) {
            payload.put("authorId", authorId);
        }
        return new Event(sourceId, "message", payload);
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
