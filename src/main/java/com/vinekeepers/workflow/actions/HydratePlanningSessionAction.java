package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomState;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.planning.PlanReadinessStatus;
import com.vinekeepers.state.planning.PlanningIntakeStage;
import com.vinekeepers.workflow.planreview.PlanningUserFacingCopy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * When the Discord event channel is a feature-room intake/spec thread, spreads workflow keys from
 * {@link FeatureRoomState} / {@link FeaturePlanState} so thread-scoped planning can run without Luna session state.
 */
public final class HydratePlanningSessionAction implements com.vinekeepers.workflow.WorkflowAction {

    private final FeatureRoomStateStore roomStore;
    private final FeaturePlanStateStore planStore;

    public HydratePlanningSessionAction(FeatureRoomStateStore roomStore, FeaturePlanStateStore planStore) {
        this.roomStore = roomStore;
        this.planStore = planStore;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (roomStore == null) {
            out.put("planningIntakeThread", "false");
            out.put("planningHydrateError", "FeatureRoomStateStore not available.");
            return out;
        }
        String channelId = firstNonBlank(
                eventChannelId(event),
                getString(bind, "channelId"),
                getString(state, "channelId"));
        if (channelId == null || channelId.isBlank()) {
            out.put("planningIntakeThread", "false");
            out.put("planningHydrateError", "Missing channel id for hydrate_planning_session.");
            return out;
        }
        Optional<FeatureRoomState> roomOpt = roomStore.getByIntakeThreadId(channelId);
        if (roomOpt.isEmpty()) {
            out.put("planningIntakeThread", "false");
            return out;
        }
        FeatureRoomState room = roomOpt.get();
        out.put("planningIntakeThread", "true");
        out.put("contextId", room.getContextId());
        out.put("channelId", room.getRoomChannelId());
        out.put("deliveryChannelId", room.getIntakeThreadId());
        String project = room.getRepo();
        String codeChange = room.getInitialRequest();
        Optional<FeaturePlanState> planOpt =
                planStore != null ? planStore.getByContextId(room.getContextId()) : Optional.empty();
        if (state != null
                && "true".equalsIgnoreCase(String.valueOf(state.get("planningWorkflowStepLimitReached")))) {
            if (planOpt.isPresent() && planStore != null) {
                FeaturePlanState p = planOpt.get();
                if (p.getPlanningIntakeStage() != PlanningIntakeStage.FAILED) {
                    planStore.update(
                            p.withPlanningOrchestrationFailure(
                                    "Workflow step limit reached. Send a short reply in this thread to retry."));
                    planOpt = planStore.getByContextId(room.getContextId());
                }
            }
            out.put("planningWorkflowStepLimitReached", "false");
        }
        if (planOpt.isPresent()) {
            FeaturePlanState plan = planOpt.get();
            if (plan.getRepoRef() != null && !plan.getRepoRef().isBlank()) {
                project = plan.getRepoRef();
            }
            if (plan.getInitialRequest() != null && !plan.getInitialRequest().isBlank()) {
                codeChange = plan.getInitialRequest();
            }
            out.put("canonicalPlanningIntakeStage", plan.getPlanningIntakeStage().name());
            if (plan.getPacketPostedAt() != null) {
                out.put("planningPacketPosted", "true");
                int v = plan.getPacketPostedChunkCount() > 0 ? plan.getPacketPostedChunkCount() : 1;
                out.put("planningPacketPostedVersion", String.valueOf(v));
            }
            if (plan.getPlanConfidence() != null && plan.getPlanConfidence().getReadinessStatus() != null) {
                String rs = PlanReadinessStatus.legacySpreadValue(plan.getPlanConfidence().getReadinessStatus());
                out.put("planReadinessStatus", rs);
                out.put("planReadinessStatusLabel", PlanningUserFacingCopy.humanizeReadinessStatus(rs));
                out.put(
                        "planReadinessCheckpointGuide",
                        PlanReadinessStatus.NEEDS_HUMAN_DECISION.equals(rs)
                                ? PlanningUserFacingCopy.readinessCheckpointGuideForDiscord()
                                : "");
            }
        }
        if (project != null && !project.isBlank()) {
            out.put("project", project);
        }
        if (codeChange != null && !codeChange.isBlank()) {
            out.put("codeChange", codeChange);
        }
        Object kick = event != null && event.getPayload() != null ? event.getPayload().get("coordinatorKickoff") : null;
        if (kick != null && ("true".equalsIgnoreCase(String.valueOf(kick).trim()) || Boolean.TRUE.equals(kick))) {
            out.put("coordinatorKickoff", "true");
        } else {
            out.put("coordinatorKickoff", "false");
        }
        Object ingress =
                event != null && event.getPayload() != null ? event.getPayload().get("planningIngressMode") : null;
        if (ingress != null && !ingress.toString().isBlank()) {
            out.put("planningIngressMode", ingress.toString().trim());
        } else if ("true".equalsIgnoreCase(String.valueOf(out.get("coordinatorKickoff")))) {
            out.put("planningIngressMode", "SYNTHETIC_KICKOFF");
        } else if ("true".equalsIgnoreCase(String.valueOf(out.get("planningIntakeThread")))) {
            out.put("planningIngressMode", "USER_MESSAGE");
        } else {
            out.put("planningIngressMode", "");
        }
        return out;
    }

    private static String eventChannelId(Event event) {
        if (event == null || event.getPayload() == null) {
            return null;
        }
        Map<String, Object> p = event.getPayload();
        Object c = p.get("channelId");
        if (c == null) {
            c = p.get("channel");
        }
        return c != null ? c.toString() : null;
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b, String c) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        if (c != null && !c.isBlank()) {
            return c;
        }
        return null;
    }
}
