package com.vinekeepers.state.planning;

import java.util.Objects;
import java.util.Optional;

/**
 * Central resolution of feature-room intake thread ↔ plan binding and coordinator bot id.
 * Order: {@link FeatureRoomStateStore} by intake thread id, {@link FeaturePlanStateStore} by intake thread id,
 * {@link FeaturePlanStateStore} by room channel id when planning is active and an intake thread is recorded.
 */
public final class PlanningIntakeBindingResolver {

    private final FeatureRoomStateStore roomStore;
    private final FeaturePlanStateStore planStore;

    public PlanningIntakeBindingResolver(FeatureRoomStateStore roomStore, FeaturePlanStateStore planStore) {
        this.roomStore = roomStore;
        this.planStore = planStore;
    }

    /**
     * @param channelId     Discord channel or thread snowflake for the event
     * @param parentChannelId parent forum / text channel when {@code channelId} is a thread; may be null
     */
    public Optional<Binding> resolve(String channelId, String parentChannelId) {
        if (channelId == null || channelId.isBlank()) {
            return Optional.empty();
        }
        FeatureRoomState room = null;
        FeaturePlanState plan = null;
        boolean channelIsIntakeThread = false;

        if (roomStore != null) {
            Optional<FeatureRoomState> byThread = roomStore.getByIntakeThreadId(channelId);
            if (byThread.isPresent()) {
                room = byThread.get();
                channelIsIntakeThread = true;
                if (planStore != null) {
                    plan = planStore.getByContextId(room.getContextId()).orElse(null);
                }
            }
        }
        if (plan == null && planStore != null) {
            Optional<FeaturePlanState> byPlanThread = planStore.getByIntakeThreadId(channelId);
            if (byPlanThread.isPresent()) {
                plan = byPlanThread.get();
                channelIsIntakeThread = true;
                if (room == null && roomStore != null) {
                    room = roomStore.getByContextId(plan.getContextId()).orElse(null);
                }
            }
        }
        if (plan == null && planStore != null) {
            Optional<FeaturePlanState> byRoom = planStore.getByRoomChannelId(channelId);
            if (byRoom.isPresent()) {
                FeaturePlanState p = byRoom.get();
                if (isActivePlanningIntake(p)
                        && p.getIntakeThreadId() != null
                        && !p.getIntakeThreadId().isBlank()) {
                    plan = p;
                    channelIsIntakeThread = false;
                    if (room == null && roomStore != null) {
                        room = roomStore.getByContextId(p.getContextId()).orElse(null);
                    }
                }
            }
        }
        // Thread under a parent: event in thread, plan indexed only by room + intakeThreadId
        if (plan == null && planStore != null && parentChannelId != null && !parentChannelId.isBlank()) {
            Optional<FeaturePlanState> parentPlan = planStore.getByRoomChannelId(parentChannelId);
            if (parentPlan.isPresent()) {
                FeaturePlanState p = parentPlan.get();
                String intake = p.getIntakeThreadId();
                if (intake != null && intake.equals(channelId) && isActivePlanningIntake(p)) {
                    plan = p;
                    channelIsIntakeThread = true;
                    if (room == null && roomStore != null) {
                        room = roomStore.getByContextId(p.getContextId()).orElse(null);
                    }
                }
            }
        }

        if (room == null && plan == null) {
            return Optional.empty();
        }
        Optional<String> coordinator = resolveCoordinatorBotId(room, plan);
        boolean active = plan != null && isActivePlanningIntake(plan);
        return Optional.of(new Binding(room, plan, coordinator, channelIsIntakeThread, active));
    }

    public static boolean isActivePlanningIntake(FeaturePlanState plan) {
        if (plan == null) {
            return false;
        }
        PlanningIntakeStage s = plan.getPlanningIntakeStage();
        return s != PlanningIntakeStage.DONE;
    }

    public static Optional<String> resolveCoordinatorBotId(FeatureRoomState room, FeaturePlanState plan) {
        if (room != null) {
            Optional<String> fromRoom = FeatureRoomStateStore.resolveCoordinatorConfiguredBotId(room);
            if (fromRoom.isPresent()) {
                return fromRoom;
            }
        }
        if (plan != null) {
            String c = plan.getCoordinatorConfiguredBotId();
            if (c != null && !c.isBlank()) {
                return Optional.of(c.trim());
            }
        }
        return Optional.empty();
    }

    public record Binding(
            FeatureRoomState roomState,
            FeaturePlanState planState,
            Optional<String> coordinatorConfiguredBotId,
            boolean eventChannelIsPlanIntakeThread,
            boolean activePlanningSession) {

        public Binding {
            coordinatorConfiguredBotId = coordinatorConfiguredBotId != null ? coordinatorConfiguredBotId : Optional.empty();
        }

        /** Plain-text or thread reply in an intake thread while planning is active (coordinator-only routing). */
        public boolean exclusiveCoordinatorThread() {
            return eventChannelIsPlanIntakeThread && activePlanningSession;
        }
    }

    @Override
    public String toString() {
        return "PlanningIntakeBindingResolver(" + Objects.hashCode(roomStore) + ")";
    }
}
