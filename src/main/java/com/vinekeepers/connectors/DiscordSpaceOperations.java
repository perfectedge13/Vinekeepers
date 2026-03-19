package com.vinekeepers.connectors;

import com.vinekeepers.state.LifecycleContextStore;

import java.util.Locale;

/**
 * Discord implementation of SpaceOperations: create text channel (room) and thread
 * using OutboundDeliveryRouter and LifecycleContextStore. Reads only request getters
 * (no state/bind map access). Returns typed CreateRoomResult/CreateThreadResult.
 */
public final class DiscordSpaceOperations implements SpaceOperations {

    /** Discord permission bits: VIEW_CHANNEL | SEND_MESSAGES for lifecycle owner override. */
    private static final long LIFECYCLE_OWNER_ALLOW = (1L << 10) | (1L << 11);

    private static final int MAX_DISCORD_CHANNEL_NAME = 100;
    private static final String FALLBACK_NAME = "lifecycle-room";
    private static final String DISCORD_PREFIX = "discord:";

    private final OutboundDeliveryRouter router;
    private final LifecycleContextStore lifecycleContextStore;

    public DiscordSpaceOperations(OutboundDeliveryRouter router, LifecycleContextStore lifecycleContextStore) {
        this.router = router;
        this.lifecycleContextStore = lifecycleContextStore;
    }

    @Override
    public CreateRoomResult createRoom(CreateRoomRequest request) {
        if (request == null) {
            return new CreateRoomResult.Failure(CreateRoomFailureReason.REQUEST_NULL);
        }
        OutboundGateway gw = router != null ? router.getDefaultGateway() : null;
        if (gw == null || !gw.isConnected()) {
            return new CreateRoomResult.Failure(CreateRoomFailureReason.GATEWAY_UNAVAILABLE);
        }
        String guildId = request.guildId();
        if (guildId == null || guildId.isBlank()) {
            String sourceId = request.sourceId();
            if (sourceId != null && sourceId.startsWith(DISCORD_PREFIX)) {
                guildId = sourceId.substring(DISCORD_PREFIX.length()).trim();
                if (guildId != null && guildId.isBlank()) guildId = null;
            }
        }
        if (guildId == null || guildId.isBlank()) {
            return new CreateRoomResult.Failure(CreateRoomFailureReason.GUILD_ID_MISSING);
        }
        String channelName = request.channelName();
        if (channelName == null || channelName.isBlank()) {
            channelName = buildChannelNameFromRequest(request.project(), request.codeChange());
        }
        channelName = normalizeChannelName(channelName);
        try {
            String channelId = gw.createTextChannel(guildId, channelName);
            if (channelId == null) {
                return new CreateRoomResult.Failure(CreateRoomFailureReason.CREATE_RETURNED_NULL);
            }
            var participantBotIds = request.participantBotIds();
            if (router != null && participantBotIds != null && !participantBotIds.isEmpty()) {
                for (String botId : participantBotIds) {
                    if (botId == null || botId.isBlank()) continue;
                    String userId = router.getSelfUserIdForBot(botId);
                    if (userId == null) {
                        return new CreateRoomResult.Failure(CreateRoomFailureReason.OWNER_USER_ID_NULL);
                    }
                    if (!gw.addPermissionOverride(channelId, guildId, userId, LIFECYCLE_OWNER_ALLOW, 0L)) {
                        return new CreateRoomResult.Failure(CreateRoomFailureReason.PERMISSION_OVERRIDE_FAILED);
                    }
                }
            } else {
                String lifecycleOwnerBotId = request.lifecycleOwnerBotId();
                if (router != null && lifecycleOwnerBotId != null && !lifecycleOwnerBotId.isBlank()) {
                    String ownerUserId = router.getSelfUserIdForBot(lifecycleOwnerBotId);
                    if (ownerUserId == null) {
                        return new CreateRoomResult.Failure(CreateRoomFailureReason.OWNER_USER_ID_NULL);
                    }
                    if (!gw.addPermissionOverride(channelId, guildId, ownerUserId, LIFECYCLE_OWNER_ALLOW, 0L)) {
                        return new CreateRoomResult.Failure(CreateRoomFailureReason.PERMISSION_OVERRIDE_FAILED);
                    }
                }
            }
            return new CreateRoomResult.Success(channelId);
        } catch (Exception e) {
            return new CreateRoomResult.Failure(CreateRoomFailureReason.EXCEPTION);
        }
    }

    @Override
    public CreateThreadResult createThread(CreateThreadRequest request) {
        if (router == null) {
            return new CreateThreadResult.Failure(CreateThreadFailureReason.ROUTER_NULL);
        }
        if (request == null) {
            return new CreateThreadResult.Failure(CreateThreadFailureReason.REQUEST_NULL);
        }
        String channelId = request.channelId();
        if (channelId == null || channelId.isBlank()) {
            return new CreateThreadResult.Failure(CreateThreadFailureReason.CHANNEL_ID_MISSING);
        }
        OutboundGateway gateway = router.getGatewayForChannel(channelId);
        if (gateway == null) {
            gateway = router.getDefaultGateway();
        }
        if (gateway == null || !gateway.isConnected()) {
            return new CreateThreadResult.Failure(CreateThreadFailureReason.GATEWAY_UNAVAILABLE);
        }
        String threadName = request.threadName();
        if (threadName == null || threadName.isBlank()) {
            threadName = "Room updates";
        }
        String threadId = gateway.createThreadChannel(channelId, threadName);
        if (threadId == null || threadId.isBlank()) {
            return new CreateThreadResult.Failure(CreateThreadFailureReason.CREATE_RETURNED_NULL);
        }
        if (lifecycleContextStore != null) {
            String contextId = request.contextId();
            if (contextId != null && !contextId.isBlank()) {
                lifecycleContextStore.setDeliveryTargetId(contextId, threadId);
            }
        }
        return new CreateThreadResult.Success(threadId);
    }

    static String normalizeChannelName(String name) {
        if (name == null || name.isBlank()) return FALLBACK_NAME;
        String normalized = name.trim()
                .replaceAll("[^a-zA-Z0-9_-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "")
                .toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return FALLBACK_NAME;
        if (normalized.length() > MAX_DISCORD_CHANNEL_NAME) {
            normalized = normalized.substring(0, MAX_DISCORD_CHANNEL_NAME);
        }
        return normalized;
    }

    /**
     * Builds a channel name from project and codeChange (same logic as former buildChannelNameFromState).
     * Used when request.channelName() is blank.
     */
    static String buildChannelNameFromRequest(String project, String codeChange) {
        String repoSegment = repoSegmentFromProject(project);
        String slug = slugFromCodeChange(codeChange);
        String suffix = Long.toHexString(System.currentTimeMillis()).toLowerCase(Locale.ROOT);
        if (suffix.length() > 8) suffix = suffix.substring(suffix.length() - 8);
        String name = (repoSegment + "-" + slug + "-" + suffix)
                .replaceAll("[^a-zA-Z0-9_-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "")
                .toLowerCase(Locale.ROOT);
        if (name.isBlank()) name = FALLBACK_NAME;
        if (name.length() > MAX_DISCORD_CHANNEL_NAME) {
            name = name.substring(0, MAX_DISCORD_CHANNEL_NAME);
        }
        return name;
    }

    private static String repoSegmentFromProject(String project) {
        if (project == null || project.isBlank()) return "repo";
        String t = project.trim();
        String path = null;
        if (t.startsWith("https://github.com/")) {
            path = t.substring("https://github.com/".length());
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
        } else if (t.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            path = t;
        }
        if (path != null && !path.isBlank()) {
            int lastSlash = path.lastIndexOf('/');
            String segment = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
            if (!segment.isBlank()) {
                return segment.replaceAll("[^a-zA-Z0-9_-]", "-").replaceAll("-+", "-").replaceAll("(^-|-$)", "").toLowerCase(Locale.ROOT);
            }
        }
        return t.replaceAll("[^a-zA-Z0-9_-]", "-").replaceAll("-+", "-").replaceAll("(^-|-$)", "").toLowerCase(Locale.ROOT);
    }

    private static String slugFromCodeChange(String codeChange) {
        if (codeChange == null || codeChange.isBlank()) return "change";
        String s = codeChange.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (s.length() > 32) s = s.substring(0, 32);
        return s.isBlank() ? "change" : s;
    }
}
