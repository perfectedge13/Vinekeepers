package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.repo.RepoWorkspaceProgressPhase;
import com.vinekeepers.state.repo.RepoWorkspaceService;
import com.vinekeepers.state.repo.RepoWorkspaceState;
import com.vinekeepers.state.repo.RepoWorkspaceStateStore;
import com.vinekeepers.state.repo.RepoWorkspaceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Resolves/materializes a repo workspace, stores {@link RepoWorkspaceState}, and links {@link FeaturePlanState}.
 * When an {@link OutboundDeliveryRouter} is supplied, posts short progress lines to the lifecycle thread as Arrietty.
 */
public final class EnsureRepoWorkspaceAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final Logger log = LoggerFactory.getLogger(EnsureRepoWorkspaceAction.class);
    private static final String ARRIETTY_BOT_ID = "arrietty";
    private static final int CHAT_DETAIL_MAX = 350;

    private final RepoWorkspaceService repoWorkspaceService;
    private final RepoWorkspaceStateStore repoWorkspaceStateStore;
    private final FeaturePlanStateStore planStateStore;
    private final FeatureRoomStateStore featureRoomStateStore;
    private final ExplicitBotSender explicitBotSender;

    public EnsureRepoWorkspaceAction(
            RepoWorkspaceService repoWorkspaceService,
            RepoWorkspaceStateStore repoWorkspaceStateStore,
            FeaturePlanStateStore planStateStore,
            FeatureRoomStateStore featureRoomStateStore,
            OutboundDeliveryRouter outboundDeliveryRouter) {
        this(
                repoWorkspaceService,
                repoWorkspaceStateStore,
                planStateStore,
                featureRoomStateStore,
                outboundDeliveryRouter != null ? outboundDeliveryRouter::sendAsExplicit : null);
    }

    public EnsureRepoWorkspaceAction(
            RepoWorkspaceService repoWorkspaceService,
            RepoWorkspaceStateStore repoWorkspaceStateStore,
            FeaturePlanStateStore planStateStore,
            FeatureRoomStateStore featureRoomStateStore,
            ExplicitBotSender explicitBotSender) {
        this.repoWorkspaceService = repoWorkspaceService;
        this.repoWorkspaceStateStore = repoWorkspaceStateStore;
        this.planStateStore = planStateStore;
        this.featureRoomStateStore = featureRoomStateStore;
        this.explicitBotSender = explicitBotSender;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        if (repoWorkspaceService == null || repoWorkspaceStateStore == null) {
            return "Repo workspace services not available.";
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            return "Missing contextId for ensure_repo_workspace.";
        }

        String rawRepo = firstNonBlank(getString(bind, "repo"), getString(state, "project"));
        if (rawRepo == null || rawRepo.isBlank()) {
            rawRepo = planStateStore != null
                    ? planStateStore.getByContextId(contextId).map(FeaturePlanState::getRepoRef).orElse(null)
                    : null;
        }
        if (rawRepo == null || rawRepo.isBlank()) {
            rawRepo = featureRoomStateStore != null
                    ? featureRoomStateStore.getByContextId(contextId).map(r -> r.getRepo()).orElse(null)
                    : null;
        }
        if (rawRepo == null || rawRepo.isBlank()) {
            return "Missing repo input for ensure_repo_workspace.";
        }

        Map<String, Object> st = state != null ? state : Map.of();
        Map<String, Object> bd = bind != null ? bind : Map.of();
        RepoWorkspaceState prior = repoWorkspaceStateStore.getByContextId(contextId).orElse(null);
        RepoWorkspaceState ensured = repoWorkspaceService.ensure(contextId, rawRepo, (phase, detail) -> {
            String line = formatProgressChatLine(phase, detail);
            if (line == null) {
                return;
            }
            postArrietty(st, bd, line);
        }, prior);
        repoWorkspaceStateStore.put(ensured);

        if (planStateStore != null) {
            planStateStore.getByContextId(contextId).ifPresent(plan -> {
                String notes = ensured.getFailureReason();
                if (notes == null || notes.isBlank()) {
                    if (ensured.getStatus() == RepoWorkspaceStatus.UNAVAILABLE) {
                        notes = "Workspace unavailable";
                    }
                }
                FeaturePlanState linked = plan.withWorkspaceLinkage(
                        ensured.getWorkspaceId(),
                        ensured.getStatus() != null ? ensured.getStatus().name() : null,
                        ensured.getLocalPath(),
                        notes);
                planStateStore.update(linked);
            });
        }

        return "OK";
    }

    private void postArrietty(Map<String, Object> state, Map<String, Object> bind, String content) {
        if (explicitBotSender == null) {
            return;
        }
        String sendTarget = resolveSendTarget(state, bind);
        if (sendTarget == null || sendTarget.isBlank()) {
            log.debug("Skipping Arrietty repo progress: no send target in workflow state");
            return;
        }
        Optional<String> err = explicitBotSender.sendAsExplicit(sendTarget, null, content, ARRIETTY_BOT_ID);
        err.ifPresent(msg -> log.warn("Arrietty repo progress not delivered: {}", msg));
    }

    /**
     * Same target resolution as {@link PostChannelMessageAction} legacy path (thread preferred).
     */
    private static String resolveSendTarget(Map<String, Object> state, Map<String, Object> bind) {
        String channelId = firstNonBlank(getString(bind, "channelId"), getString(state, "channelId"));
        String deliveryChannelId = firstNonBlank(getString(bind, "deliveryChannelId"), getString(state, "deliveryChannelId"));
        String sendTarget = firstNonBlank(deliveryChannelId, channelId);
        if (CreateThreadAction.THREAD_CREATE_FAILED.equals(sendTarget)) {
            sendTarget = channelId;
        }
        return sendTarget;
    }

    static String formatProgressChatLine(RepoWorkspaceProgressPhase phase, String detail) {
        return switch (phase) {
            case RESOLVED_REF, VERIFYING_GIT -> null;
            case CREATING_WORKSPACE_ROOT -> "**Arrietty:** Preparing workspace directories…";
            case REMOVING_STALE_CLONE -> "**Arrietty:** Removing previous checkout…";
            case CLONING -> "**Arrietty:** Cloning repository…";
            case READY_LOCAL -> "**Arrietty:** Using local repository"
                    + (detail != null && !detail.isBlank() ? ": `" + truncateChat(detail) + "`" : ".");
            case READY_CLONED -> "**Arrietty:** Repo ready: `" + (detail != null ? truncateChat(detail) : "?") + "`";
            case FAILED -> "**Arrietty:** Workspace issue — " + truncateChat(detail != null ? detail : "unknown error");
        };
    }

    private static String truncateChat(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.length() <= CHAT_DETAIL_MAX) {
            return t;
        }
        return t.substring(0, CHAT_DETAIL_MAX) + "…";
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
