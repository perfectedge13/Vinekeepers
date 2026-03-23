package com.vinekeepers.workflow.actions;

import com.vinekeepers.bot.NormalizedEventContext;
import com.vinekeepers.core.VinekeepersEngine;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.planning.PlanningIntakeBindingResolver;

import java.util.HashMap;
import java.util.Map;

/**
 * After the intake bot posts the intake/spec handoff, explicitly runs the coordinator bot's thread workflow once
 * using an in-process synthetic Discord message (same thread id as channelId + threadId). Does not post a
 * user-visible dummy message to Discord.
 */
public final class StartCoordinatorPlanningAction implements com.vinekeepers.workflow.WorkflowAction {

    private final VinekeepersEngine engine;
    private final FeatureRoomStateStore featureRoomStateStore;
    private final FeaturePlanStateStore featurePlanStateStore;

    public StartCoordinatorPlanningAction(
            VinekeepersEngine engine,
            FeatureRoomStateStore featureRoomStateStore,
            FeaturePlanStateStore featurePlanStateStore) {
        this.engine = engine;
        this.featureRoomStateStore = featureRoomStateStore;
        this.featurePlanStateStore = featurePlanStateStore;
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
        if ((coordinator == null || coordinator.isBlank()) && featureRoomStateStore != null) {
            coordinator = featureRoomStateStore
                    .getByIntakeThreadId(intakeThreadId)
                    .flatMap(FeatureRoomStateStore::resolveCoordinatorConfiguredBotId)
                    .orElse(null);
        }
        if ((coordinator == null || coordinator.isBlank()) && featurePlanStateStore != null) {
            coordinator = featurePlanStateStore
                    .getByIntakeThreadId(intakeThreadId)
                    .map(p -> p.getCoordinatorConfiguredBotId())
                    .filter(s -> s != null && !s.isBlank())
                    .orElse(null);
        }
        if ((coordinator == null || coordinator.isBlank())
                && featureRoomStateStore != null
                && featurePlanStateStore != null) {
            String parent =
                    parentEvent != null ? NormalizedEventContext.from(parentEvent).getParentChannelId() : null;
            coordinator =
                    new PlanningIntakeBindingResolver(featureRoomStateStore, featurePlanStateStore)
                            .resolve(intakeThreadId.trim(), parent)
                            .flatMap(PlanningIntakeBindingResolver.Binding::coordinatorConfiguredBotId)
                            .orElse(null);
        }
        if (coordinator == null || coordinator.isBlank()) {
            return "start_coordinator_planning: missing coordinatorBotId (set bind/state coordinatorBotId or ensure "
                    + "feature room state exists for this intake thread with a primary coordinator participant).";
        }
        Event synthetic = buildSyntheticThreadMessage(parentEvent, intakeThreadId.trim());
        return engine.dispatchCoordinatorPlanningKickoff(synthetic, coordinator.trim());
    }

    private static Event buildSyntheticThreadMessage(Event parentEvent, String intakeThreadId) {
        String sourceId = parentEvent != null ? parentEvent.getSourceId() : "discord:synthetic";
        NormalizedEventContext ctx = NormalizedEventContext.from(parentEvent);
        String authorId = ctx.getActorId() != null ? ctx.getActorId() : "";
        Map<String, Object> payload = new HashMap<>();
        payload.put("channelId", intakeThreadId);
        payload.put("threadId", intakeThreadId);
        payload.put("coordinatorKickoff", "true");
        payload.put("planningIngressMode", "SYNTHETIC_KICKOFF");
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
