package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.profile.ArtifactStateFactory;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomState;
import com.vinekeepers.state.planning.FeatureRoomStateStore;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates initial {@link FeaturePlanState} from {@link FeatureRoomState} (by contextId) or bind/state fallbacks.
 */
public final class InitializeFeaturePlanStateAction implements com.vinekeepers.workflow.WorkflowAction {

    public static final String DEFAULT_PROFILE_ID = "software_feature_planning";

    private final FeaturePlanStateStore planStateStore;
    private final FeatureRoomStateStore featureRoomStateStore;
    private final WorkProfileRegistry workProfileRegistry;

    public InitializeFeaturePlanStateAction(
            FeaturePlanStateStore planStateStore,
            FeatureRoomStateStore featureRoomStateStore,
            WorkProfileRegistry workProfileRegistry) {
        this.planStateStore = planStateStore;
        this.featureRoomStateStore = featureRoomStateStore;
        this.workProfileRegistry = workProfileRegistry;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        if (planStateStore == null) {
            return "FeaturePlanStateStore not available.";
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            return "Missing contextId for initialize_feature_plan_state.";
        }

        Optional<FeatureRoomState> roomOpt =
                featureRoomStateStore != null ? featureRoomStateStore.getByContextId(contextId) : Optional.empty();
        FeatureRoomState room = roomOpt.orElse(null);

        String featureId = firstNonBlank(getString(bind, "featureId"), getString(state, "featureId"));
        String featureSlug = firstNonBlank(getString(bind, "featureSlug"), getString(state, "featureSlug"));
        String roomChannelId = firstNonBlank(getString(bind, "roomChannelId"), getString(state, "channelId"));
        String intakeThreadId = firstNonBlank(getString(bind, "intakeThreadId"), getString(state, "deliveryChannelId"));
        String repoRef = firstNonBlank(getString(bind, "repo"), getString(state, "project"));
        String initialRequest = firstNonBlank(getString(bind, "initialRequest"), getString(state, "codeChange"));
        String title = firstNonBlank(getString(bind, "planTitle"), getString(state, "planTitle"));

        if (room != null) {
            if (featureId == null || featureId.isBlank()) {
                featureId = room.getFeatureId();
            }
            if (featureSlug == null || featureSlug.isBlank()) {
                featureSlug = room.getFeatureSlug();
            }
            if (roomChannelId == null || roomChannelId.isBlank()) {
                roomChannelId = room.getRoomChannelId();
            }
            if (intakeThreadId == null || intakeThreadId.isBlank()) {
                intakeThreadId = room.getIntakeThreadId();
            }
            if (repoRef == null || repoRef.isBlank()) {
                repoRef = room.getRepo();
            }
            if (initialRequest == null || initialRequest.isBlank()) {
                initialRequest = room.getInitialRequest();
            }
        }

        if (featureId == null || featureId.isBlank()) {
            featureId = "feat-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        if (featureSlug == null || featureSlug.isBlank()) {
            featureSlug = "feature";
        }
        if (roomChannelId == null || roomChannelId.isBlank()) {
            return "Missing roomChannelId/channelId for initialize_feature_plan_state.";
        }

        if (title == null || title.isBlank()) {
            title = truncateTitle(initialRequest);
        }

        String profileId = firstNonBlank(getString(bind, "profileId"), getString(state, "profileId"));
        if (profileId == null || profileId.isBlank()) {
            profileId = DEFAULT_PROFILE_ID;
        }
        if (workProfileRegistry == null) {
            return "WorkProfileRegistry not available; cannot initialize plan with profile.";
        }
        if (workProfileRegistry.isEmpty()) {
            return "WorkProfileRegistry is empty (config/work-profiles.yaml missing or invalid); cannot initialize plan with profile.";
        }
        WorkProfileDefinition profileDef = workProfileRegistry.get(profileId).orElse(null);
        if (profileDef == null) {
            return "Unknown profileId: " + profileId;
        }

        FeaturePlanState plan = new FeaturePlanState(
                contextId,
                featureId,
                featureSlug,
                roomChannelId,
                intakeThreadId,
                repoRef,
                title,
                initialRequest,
                "PLANNING",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                FeaturePlanState.initialSectionStatuses(),
                null,
                null,
                null,
                null,
                null,
                null,
                profileId,
                ArtifactStateFactory.emptyArtifacts(profileDef),
                null,
                null);
        planStateStore.put(plan);
        return "OK";
    }

    private static String truncateTitle(String initialRequest) {
        if (initialRequest == null || initialRequest.isBlank()) {
            return "Planning";
        }
        String oneLine = initialRequest.replace('\n', ' ').trim();
        return oneLine.length() > 120 ? oneLine.substring(0, 120) + "…" : oneLine;
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }
}
