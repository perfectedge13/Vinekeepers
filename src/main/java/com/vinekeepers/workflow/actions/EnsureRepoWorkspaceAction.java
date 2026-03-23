package com.vinekeepers.workflow.actions;

import com.vinekeepers.bot.BotCatalog;
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
 * When an {@link OutboundDeliveryRouter} is supplied, posts short progress lines using the workflow bot id
 * ({@code __botId}) or optional bind {@code repoProgressAsBotId} / {@code repoProgressPersonaName}.
 */
public final class EnsureRepoWorkspaceAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final Logger log = LoggerFactory.getLogger(EnsureRepoWorkspaceAction.class);
    private static final int CHAT_DETAIL_MAX = 350;

    private final RepoWorkspaceService repoWorkspaceService;
    private final RepoWorkspaceStateStore repoWorkspaceStateStore;
    private final FeaturePlanStateStore planStateStore;
    private final FeatureRoomStateStore featureRoomStateStore;
    private final BotCatalog botCatalog;
    private final ExplicitBotSender explicitBotSender;

    public EnsureRepoWorkspaceAction(
            RepoWorkspaceService repoWorkspaceService,
            RepoWorkspaceStateStore repoWorkspaceStateStore,
            FeaturePlanStateStore planStateStore,
            FeatureRoomStateStore featureRoomStateStore,
            BotCatalog botCatalog,
            OutboundDeliveryRouter outboundDeliveryRouter) {
        this(
                repoWorkspaceService,
                repoWorkspaceStateStore,
                planStateStore,
                featureRoomStateStore,
                botCatalog,
                outboundDeliveryRouter != null ? outboundDeliveryRouter::sendAsExplicit : null);
    }

    public EnsureRepoWorkspaceAction(
            RepoWorkspaceService repoWorkspaceService,
            RepoWorkspaceStateStore repoWorkspaceStateStore,
            FeaturePlanStateStore planStateStore,
            FeatureRoomStateStore featureRoomStateStore,
            BotCatalog botCatalog,
            ExplicitBotSender explicitBotSender) {
        this.repoWorkspaceService = repoWorkspaceService;
        this.repoWorkspaceStateStore = repoWorkspaceStateStore;
        this.planStateStore = planStateStore;
        this.featureRoomStateStore = featureRoomStateStore;
        this.botCatalog = botCatalog != null ? botCatalog : new BotCatalog();
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
        String progressLabel = resolveProgressPersonaLabel(st, bd);
        RepoWorkspaceState prior = repoWorkspaceStateStore.getByContextId(contextId).orElse(null);
        RepoWorkspaceState ensured = repoWorkspaceService.ensure(contextId, rawRepo, (phase, detail) -> {
            String line = formatProgressChatLine(progressLabel, phase, detail);
            if (line == null) {
                return;
            }
            postProgress(st, bd, line);
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

    private void postProgress(Map<String, Object> state, Map<String, Object> bind, String content) {
        if (explicitBotSender == null) {
            return;
        }
        String sendTarget = resolveSendTarget(state, bind);
        if (sendTarget == null || sendTarget.isBlank()) {
            log.debug("Skipping repo progress post: no send target in workflow state");
            return;
        }
        String botId = resolveSenderBotId(state, bind);
        if (botId == null || botId.isBlank()) {
            log.debug("Skipping repo progress post: missing __botId / repoProgressAsBotId");
            return;
        }
        Optional<String> err = explicitBotSender.sendAsExplicit(sendTarget, null, content, botId.trim());
        err.ifPresent(msg -> log.warn("Repo workspace progress not delivered: {}", msg));
    }

    private String resolveSenderBotId(Map<String, Object> state, Map<String, Object> bind) {
        return firstNonBlank(
                getString(bind, "repoProgressAsBotId"),
                getString(state, "repoProgressAsBotId"),
                getString(state, "__botId"));
    }

    private String resolveProgressPersonaLabel(Map<String, Object> state, Map<String, Object> bind) {
        String explicit = firstNonBlank(
                getString(bind, "repoProgressPersonaName"),
                getString(state, "repoProgressPersonaName"));
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim();
        }
        String bid = resolveSenderBotId(state, bind);
        if (bid != null && !bid.isBlank()) {
            String fromCat = botCatalog.displayNameForBot(bid.trim());
            if (fromCat != null && !fromCat.isBlank()) {
                return fromCat;
            }
            return bid.trim();
        }
        return "Coordinator";
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

    /** Default persona label {@code Coordinator} for tests and callers that omit bind/state. */
    static String formatProgressChatLine(RepoWorkspaceProgressPhase phase, String detail) {
        return formatProgressChatLine("Coordinator", phase, detail);
    }

    static String formatProgressChatLine(String personaLabel, RepoWorkspaceProgressPhase phase, String detail) {
        String label = personaLabel != null && !personaLabel.isBlank() ? personaLabel.trim() : "Coordinator";
        return switch (phase) {
            case RESOLVED_REF, VERIFYING_GIT -> null;
            case CREATING_WORKSPACE_ROOT -> "**" + label + ":** Preparing workspace directories…";
            case REMOVING_STALE_CLONE -> "**" + label + ":** Removing previous checkout…";
            case CLONING -> "**" + label + ":** Cloning repository…";
            case READY_LOCAL -> "**" + label + ":** Using local repository"
                    + (detail != null && !detail.isBlank() ? ": `" + truncateChat(detail) + "`" : ".");
            case READY_CLONED -> "**" + label + ":** Repo ready: `" + (detail != null ? truncateChat(detail) : "?") + "`";
            case FAILED -> "**" + label + ":** Workspace issue — " + truncateChat(detail != null ? detail : "unknown error");
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

    private static String firstNonBlank(String a, String b, String c) {
        String x = firstNonBlank(a, b);
        return x != null ? x : c;
    }
}
