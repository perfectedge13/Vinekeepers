package com.vinekeepers.bot;

import com.vinekeepers.debug.AgentDebugLog;
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Routes events to bot ids using event filters (from RoutingRule).
 * When lifecycle context exists for a Discord channel and the owner bot has handlesOwnedSpaces,
 * single-owner precedence applies: only the owner bot is returned for that channel.
 * When FeatureRoomState exists: room-channel events route to the primary coordinator only (low-noise);
 * intake/spec thread events route to all participant configuredBotIds
 * in stable role order (visible multi-role collaboration). Legacy single-owner channels unchanged when
 * no feature room applies.
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

        // #region agent log
        if (context != null && "discord".equals(context.getSourceType()) && "message".equals(context.getEventType())) {
            String tp = context.getText();
            if (tp != null && tp.length() > 120) {
                tp = tp.substring(0, 120);
            }
            if (tp == null) {
                tp = "";
            }
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("channelId", String.valueOf(context.getChannelId()));
            d.put("mentions", context.getMentions().toString());
            d.put("filterBotIds", filterBotIds.toString());
            d.put("textPrefix", tp);
            AgentDebugLog.log("H1", "Router.route:filter", "after_rule_scan", d);
        }
        // #endregion

        if (context != null && "discord".equals(context.getSourceType())) {
            String channelId = context.getChannelId();
            if (channelId != null && !channelId.isBlank()) {
                if (featureRoomStateStore != null) {
                    Optional<FeatureRoomState> byThread = featureRoomStateStore.getByIntakeThreadId(channelId);
                    Optional<FeatureRoomState> byRoom = featureRoomStateStore.getByRoomChannelId(channelId);
                    if (byThread.isPresent()) {
                        FeatureRoomState threadRoom = byThread.get();
                        if ("interaction".equals(context.getEventType())) {
                            Optional<String> coordinator = FeatureRoomStateStore.resolveCoordinatorConfiguredBotId(threadRoom);
                            if (coordinator.isPresent()) {
                                // #region agent log
                                AgentDebugLog.log("H3", "Router.route:featureRoom", "override_intake_thread_coordinator",
                                        Map.of("channelId", String.valueOf(channelId), "bot", coordinator.get()));
                                // #endregion
                                return List.of(coordinator.get());
                            }
                            log.warn("Intake thread has feature room state but no coordinator; falling back to participant list");
                        }
                        List<String> participantBotIds = featureRoomStateStore.getParticipantBotIds(threadRoom);
                        if (!participantBotIds.isEmpty()) {
                            // #region agent log
                            AgentDebugLog.log("H3", "Router.route:featureRoom", "override_intake_thread_participants",
                                    Map.of("channelId", String.valueOf(channelId), "bots", participantBotIds.toString()));
                            // #endregion
                            return List.copyOf(participantBotIds);
                        }
                    }
                    if (byRoom.isPresent()) {
                        Optional<String> coordinator = FeatureRoomStateStore.resolveCoordinatorConfiguredBotId(byRoom.get());
                        if (coordinator.isPresent()) {
                            // #region agent log
                            AgentDebugLog.log("H3", "Router.route:featureRoom", "override_room_channel_coordinator",
                                    Map.of("channelId", String.valueOf(channelId), "bot", coordinator.get()));
                            // #endregion
                            return List.of(coordinator.get());
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
                                // #region agent log
                                AgentDebugLog.log("H3", "Router.route:lifecycle", "override_single_owner",
                                        Map.of("channelId", String.valueOf(channelId), "ownerBotId", ownerBotId));
                                // #endregion
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
                if (channel == null || !f.getDiscordChannels().contains(channel)) {
                    // #region agent log
                    if (f.getDiscordMention() != null && "gadget".equalsIgnoreCase(f.getDiscordMention().trim())) {
                        AgentDebugLog.log("H1", "Router.matches", "discord_channels_reject",
                                Map.of("eventChannelId", String.valueOf(channel),
                                        "allowedChannels", f.getDiscordChannels().toString()));
                    }
                    // #endregion
                    return false;
                }
            }
            // Only enforce trigger/mention on message events; interactions have no mentions so use author/channel only
            if (!"interaction".equals(kind)) {
                if (f.getDiscordTrigger() != null && !f.getDiscordTrigger().isEmpty()) {
                    String text = context.getText();
                    if (text == null || !text.contains(f.getDiscordTrigger())) return false;
                }
                if (f.getDiscordMention() != null && !f.getDiscordMention().isBlank()) {
                    String mention = f.getDiscordMention().trim().toLowerCase(Locale.ROOT);
                    if (!context.getMentions().contains(mention)) {
                        // #region agent log
                        if ("gadget".equals(mention)) {
                            AgentDebugLog.log("H2", "Router.matches", "discord_mention_reject",
                                    Map.of("expectedMention", mention,
                                            "actualMentions", context.getMentions().toString()));
                        }
                        // #endregion
                        return false;
                    }
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
