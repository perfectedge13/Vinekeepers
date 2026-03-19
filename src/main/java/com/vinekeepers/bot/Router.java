package com.vinekeepers.bot;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.LifecycleContext;
import com.vinekeepers.state.LifecycleContextStore;
import com.vinekeepers.state.planning.FeatureRoomState;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Routes events to bot ids using event filters (from RoutingRule).
 * When lifecycle context exists for a Discord channel and the owner bot has handlesOwnedSpaces,
 * single-owner precedence applies: only the owner bot is returned for that channel.
 * When FeatureRoomState exists for the room (by channel or delivery target), returns all four
 * participant configuredBotIds in stable order (feature room response policy: only Orchestrator
 * replies by default; other participants reply when invoked via action e.g. post_channel_message asRole).
 */
public final class Router {

    private static final Logger log = LoggerFactory.getLogger(Router.class);

    private final List<RoutingRule> routings = new ArrayList<>();
    private final LifecycleContextStore lifecycleContextStore;
    private final FeatureRoomStateStore featureRoomStateStore;
    private volatile Map<String, Boolean> handlesOwnedSpacesByBotId = Map.of();

    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    public Router() {
        this.lifecycleContextStore = null;
        this.featureRoomStateStore = null;
    }

    public Router(LifecycleContextStore lifecycleContextStore) {
        this.lifecycleContextStore = lifecycleContextStore;
        this.featureRoomStateStore = null;
    }

    public Router(LifecycleContextStore lifecycleContextStore, FeatureRoomStateStore featureRoomStateStore) {
        this.lifecycleContextStore = lifecycleContextStore;
        this.featureRoomStateStore = featureRoomStateStore;
    }

    /**
     * Set which bot ids have handlesOwnedSpaces (called from config load).
     */
    public void setHandlesOwnedSpacesByBotId(Map<String, Boolean> handlesOwnedSpacesByBotId) {
        this.handlesOwnedSpacesByBotId = handlesOwnedSpacesByBotId != null ? Map.copyOf(handlesOwnedSpacesByBotId) : Map.of();
    }

    public void addRouting(RoutingRule routing) {
        routings.add(Objects.requireNonNull(routing));
    }

    public void clear() {
        routings.clear();
    }

    /**
     * Returns the list of bot ids that should handle this event.
     * Filter-based matching first; for Discord events in a channel with a lifecycle context whose owner
     * has handlesOwnedSpaces, single-owner precedence applies and only the owner is returned.
     */
    public List<String> route(Event event) {
        NormalizedEventContext context = event != null ? NormalizedEventContext.from(event) : null;
        List<String> filterBotIds = new ArrayList<>();
        for (RoutingRule r : routings) {
            if (matches(r.getFilter(), event)) {
                filterBotIds.add(r.getBotId());
            }
        }

        if (context != null && "discord".equals(context.getSourceType())) {
            String channelId = context.getChannelId();
            if (channelId != null && !channelId.isBlank()) {
                if (featureRoomStateStore != null) {
                    Optional<FeatureRoomState> featureRoom = featureRoomStateStore.getByRoomChannelId(channelId);
                    if (featureRoom.isEmpty()) {
                        featureRoom = featureRoomStateStore.getByDeliveryTargetId(channelId);
                    }
                    if (featureRoom.isPresent()) {
                        List<String> participantBotIds = featureRoomStateStore.getParticipantBotIds(featureRoom.get());
                        if (!participantBotIds.isEmpty()) {
                            return List.copyOf(participantBotIds);
                        }
                    }
                }
                if (lifecycleContextStore != null) {
                    var ctxOpt = lifecycleContextStore.getByChannelId(channelId);
                    if (ctxOpt.isEmpty()) {
                        ctxOpt = lifecycleContextStore.getByDeliveryTargetId(channelId);
                    }
                    if (ctxOpt.isPresent()) {
                        LifecycleContext lc = ctxOpt.get();
                        String ownerBotId = lc.getConfiguredBotId();
                        if (ownerBotId != null && !ownerBotId.isBlank()) {
                            if (Boolean.TRUE.equals(handlesOwnedSpacesByBotId.get(ownerBotId))) {
                                return List.of(ownerBotId);
                            }
                            log.warn("Channel has lifecycle owner bot {} but that bot does not have handlesOwnedSpaces; using filter-based routing", ownerBotId);
                        }
                    }
                }
            }
        }

        if (filterBotIds.isEmpty() && event != null && context != null) {
            log.debug("No bot matched: source={}, kind={}, authorId={}, actorUsername={}, mentions={}, channelId={}",
                    context.getSourceType(), context.getEventType(), context.getActorId(), context.getActorUsername(),
                    context.getMentions(), context.getChannelId());
        }
        return dedupe(filterBotIds);
    }

    private static List<String> dedupe(List<String> botIds) {
        if (botIds.size() <= 1) return botIds;
        Set<String> seen = new LinkedHashSet<>(botIds);
        return List.copyOf(seen);
    }

    private boolean matches(RoutingFilter f, Event event) {
        NormalizedEventContext context = NormalizedEventContext.from(event);
        String sourceType = context.getSourceType();
        String kind = context.getEventType();

        if ("discord".equals(sourceType)) {
            if (!f.getDiscordAuthors().isEmpty()) {
                String actorId = context.getActorId();
                String actorUsername = context.getActorUsername();
                String usernameNorm = actorUsername != null ? actorUsername.trim().toLowerCase(Locale.ROOT) : null;
                boolean authorMatch = f.getDiscordAuthors().stream().anyMatch(entry -> {
                    if (entry == null || entry.isBlank()) return false;
                    String trimmed = entry.trim();
                    if (isNumeric(trimmed)) {
                        return trimmed.equals(actorId);
                    }
                    return usernameNorm != null && !usernameNorm.isEmpty()
                            && trimmed.toLowerCase(Locale.ROOT).equals(usernameNorm);
                });
                if (!authorMatch) return false;
            }
            if (!f.getDiscordChannels().isEmpty()) {
                String channel = context.getChannelId();
                if (channel == null || !f.getDiscordChannels().contains(channel)) return false;
            }
            // Only enforce trigger/mention on message events; interactions have no mentions so use author/channel only
            if (!"interaction".equals(kind)) {
                if (f.getDiscordTrigger() != null && !f.getDiscordTrigger().isEmpty()) {
                    String text = context.getText();
                    if (text == null || !text.contains(f.getDiscordTrigger())) return false;
                }
                if (f.getDiscordMention() != null && !f.getDiscordMention().isBlank()) {
                    String mention = f.getDiscordMention().trim().toLowerCase(Locale.ROOT);
                    if (!context.getMentions().contains(mention)) return false;
                }
            }
            return true;
        }

        if ("github".equals(sourceType) || kind != null && kind.toLowerCase().contains("pr")) {
            if (!f.getRepos().isEmpty()) {
                String repo = context.getRepo();
                if (repo == null || !f.getRepos().contains(repo)) return false;
            }
            if (!f.getPrLabels().isEmpty()) {
                List<String> labels = context.getLabels();
                if (labels == null || labels.stream().noneMatch(f.getPrLabels()::contains)) return false;
            }
            if (!f.getPrAuthors().isEmpty()) {
                String author = context.getActorId();
                if (author == null || !f.getPrAuthors().contains(author)) return false;
            }
            return true;
        }

        return true;
    }
}
