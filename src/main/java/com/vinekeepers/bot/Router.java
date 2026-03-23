package com.vinekeepers.bot;

import com.vinekeepers.debug.AgentDebugLog;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.LifecycleContext;
import com.vinekeepers.state.LifecycleContextStore;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomState;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.planning.PlanningIntakeBindingResolver;
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
import java.util.regex.Pattern;

/**
 * Routes events to bot ids using event filters (from RoutingRule).
 * When lifecycle context exists for a Discord channel and the owner bot has handlesOwnedSpaces,
 * single-owner precedence applies: only the owner bot is returned for that channel.
 * When FeatureRoomState exists: room-channel events route to the primary coordinator only (low-noise);
 * intake/spec thread messages route to the coordinator only (single planning ingress; role passes stay inside the
 * coordinator workflow). Intake-thread interactions also route to the coordinator only.
 * Legacy single-owner channels unchanged when no feature room applies.
 */
public final class Router {

    private static final Logger log = LoggerFactory.getLogger(Router.class);
    private static final Pattern DISCORD_ROLE_PING_PATTERN = Pattern.compile("<@&([0-9]+)>");

    private final List<RoutingRule> routings = new ArrayList<>();
    private final LifecycleContextStore lifecycleContextStore;
    private final FeatureRoomStateStore featureRoomStateStore;
    private final PlanningIntakeBindingResolver planningIntakeBindingResolver;
    private volatile Map<String, Boolean> handlesOwnedSpacesByBotId = Map.of();
    private volatile String planningCoordinatorFallbackBotId = "";

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
        this.planningIntakeBindingResolver = null;
    }

    public Router(LifecycleContextStore lifecycleContextStore) {
        this.lifecycleContextStore = lifecycleContextStore;
        this.featureRoomStateStore = null;
        this.planningIntakeBindingResolver = null;
    }

    public Router(LifecycleContextStore lifecycleContextStore, FeatureRoomStateStore featureRoomStateStore) {
        this.lifecycleContextStore = lifecycleContextStore;
        this.featureRoomStateStore = featureRoomStateStore;
        this.planningIntakeBindingResolver =
                featureRoomStateStore != null
                        ? new PlanningIntakeBindingResolver(featureRoomStateStore, null)
                        : null;
    }

    public Router(
            LifecycleContextStore lifecycleContextStore,
            FeatureRoomStateStore featureRoomStateStore,
            FeaturePlanStateStore featurePlanStateStore,
            String planningCoordinatorFallbackBotId) {
        this.lifecycleContextStore = lifecycleContextStore;
        this.featureRoomStateStore = featureRoomStateStore;
        this.planningIntakeBindingResolver =
                featureRoomStateStore != null || featurePlanStateStore != null
                        ? new PlanningIntakeBindingResolver(featureRoomStateStore, featurePlanStateStore)
                        : null;
        this.planningCoordinatorFallbackBotId =
                planningCoordinatorFallbackBotId != null && !planningCoordinatorFallbackBotId.isBlank()
                        ? planningCoordinatorFallbackBotId.trim()
                        : "";
    }

    /**
     * Set which bot ids have handlesOwnedSpaces (called from config load).
     */
    public void setHandlesOwnedSpacesByBotId(Map<String, Boolean> handlesOwnedSpacesByBotId) {
        this.handlesOwnedSpacesByBotId = handlesOwnedSpacesByBotId != null ? Map.copyOf(handlesOwnedSpacesByBotId) : Map.of();
    }

    /**
     * Bot id used when an intake thread is clearly coordinator-owned but coordinator metadata was lost (in-memory
     * eviction). Set from config at load time — not a routing rule id hardcoded in match logic.
     */
    public void setPlanningCoordinatorFallbackBotId(String botId) {
        this.planningCoordinatorFallbackBotId =
                botId != null && !botId.isBlank() ? botId.trim() : "";
    }

    public boolean isCoordinatorExclusivePlanningDiscordEvent(Event event) {
        NormalizedEventContext ctx = event != null ? NormalizedEventContext.from(event) : null;
        if (ctx == null || !"discord".equals(ctx.getSourceType())) {
            return false;
        }
        if (!"message".equals(ctx.getEventType()) && !"interaction".equals(ctx.getEventType())) {
            return false;
        }
        String channelId = ctx.getChannelId();
        if (channelId == null || channelId.isBlank() || planningIntakeBindingResolver == null) {
            return false;
        }
        return planningIntakeBindingResolver
                .resolve(channelId, ctx.getParentChannelId())
                .filter(PlanningIntakeBindingResolver.Binding::exclusiveCoordinatorThread)
                .isPresent();
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
            d.put("parentChannelId", String.valueOf(context.getParentChannelId()));
            d.put("mentions", context.getMentions().toString());
            d.put("filterBotIds", filterBotIds.toString());
            d.put("textPrefix", tp);
            AgentDebugLog.log("H1", "Router.route:filter", "after_rule_scan", d);
        }
        // #endregion

        if (context != null && "discord".equals(context.getSourceType())) {
            String channelId = context.getChannelId();
            if (channelId != null && !channelId.isBlank()) {
                if (planningIntakeBindingResolver != null) {
                    Optional<PlanningIntakeBindingResolver.Binding> bound =
                            planningIntakeBindingResolver.resolve(channelId, context.getParentChannelId());
                    if (bound.isPresent()) {
                        PlanningIntakeBindingResolver.Binding b = bound.get();
                        if (b.exclusiveCoordinatorThread()) {
                            Optional<String> coord = b.coordinatorConfiguredBotId();
                            String route =
                                    coord.filter(id -> !id.isBlank())
                                            .orElseGet(
                                                    () ->
                                                            planningCoordinatorFallbackBotId.isBlank()
                                                                    ? null
                                                                    : planningCoordinatorFallbackBotId);
                            if (route != null && !route.isBlank()) {
                                AgentDebugLog.log(
                                        "H3",
                                        "Router.route:planningBinding",
                                        "exclusive_intake_thread_coordinator",
                                        Map.of("channelId", channelId, "bot", route));
                                return List.of(route);
                            }
                            log.warn(
                                    "Active planning intake thread {} but coordinator could not be resolved; fail-closed (empty route)",
                                    channelId);
                            return List.of();
                        }
                    }
                }
                if (featureRoomStateStore != null) {
                    Optional<FeatureRoomState> byThread = featureRoomStateStore.getByIntakeThreadId(channelId);
                    Optional<FeatureRoomState> byRoom = featureRoomStateStore.getByRoomChannelId(channelId);
                    if (byThread.isPresent()) {
                        FeatureRoomState threadRoom = byThread.get();
                        Optional<String> coordinator = FeatureRoomStateStore.resolveCoordinatorConfiguredBotId(threadRoom);
                        if ("interaction".equals(context.getEventType())) {
                            if (coordinator.isPresent()) {
                                // #region agent log
                                AgentDebugLog.log("H3", "Router.route:featureRoom", "override_intake_thread_coordinator",
                                        Map.of("channelId", String.valueOf(channelId), "bot", coordinator.get()));
                                // #endregion
                                return List.of(coordinator.get());
                            }
                            String fb =
                                    planningCoordinatorFallbackBotId.isBlank() ? null : planningCoordinatorFallbackBotId;
                            if (fb != null) {
                                log.warn(
                                        "Intake thread has feature room state but no coordinator; routing to configured planning fallback bot");
                                return List.of(fb);
                            }
                            log.warn("Intake thread has feature room state but no coordinator; fail-closed (empty route)");
                            return List.of();
                        }
                        if ("message".equals(context.getEventType()) && coordinator.isPresent()) {
                            // #region agent log
                            AgentDebugLog.log("H3", "Router.route:featureRoom", "override_intake_thread_coordinator_message",
                                    Map.of("channelId", String.valueOf(channelId), "bot", coordinator.get()));
                            // #endregion
                            return List.of(coordinator.get());
                        }
                        if ("message".equals(context.getEventType())) {
                            String fb =
                                    planningCoordinatorFallbackBotId.isBlank() ? null : planningCoordinatorFallbackBotId;
                            if (fb != null) {
                                log.warn(
                                        "Intake thread message without coordinator metadata; routing to planning fallback bot");
                                return List.of(fb);
                            }
                            return List.of();
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
            String textPrefix = context.getText();
            if (textPrefix != null && textPrefix.length() > 160) {
                textPrefix = textPrefix.substring(0, 160) + "…";
            }
            Object ingestBotId = context.getMetadata().get("ingestBotId");
            boolean rolePingDetected = containsDiscordRolePing(textPrefix);
            String routingHint = rolePingDetected
                    ? "discord role pings do not satisfy discordMention; use a direct bot mention"
                    : "";
            log.info(
                    "No routing rule matched: source={}, kind={}, channelId={}, parentChannelId={}, authorId={}, actorUsername={}, mentions={}, ingestBotId={}, rolePingDetected={}, routingHint={}, textPrefix={}",
                    context.getSourceType(),
                    context.getEventType(),
                    context.getChannelId(),
                    context.getParentChannelId(),
                    context.getActorId(),
                    context.getActorUsername(),
                    context.getMentions(),
                    ingestBotId != null ? String.valueOf(ingestBotId) : "",
                    rolePingDetected,
                    routingHint,
                    textPrefix != null ? textPrefix : "");
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
            if (!f.getDiscordChannelsExclude().isEmpty()) {
                if (matchesDiscordChannel(f.getDiscordChannelsExclude(), context)) {
                    return false;
                }
            }
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
                if (!matchesDiscordChannel(f.getDiscordChannels(), context)) {
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

    private static boolean matchesDiscordChannel(Set<String> configuredChannels, NormalizedEventContext context) {
        String channelId = context.getChannelId();
        if (channelId != null && !channelId.isBlank() && configuredChannels.contains(channelId)) {
            return true;
        }
        String parentChannelId = context.getParentChannelId();
        return parentChannelId != null
                && !parentChannelId.isBlank()
                && configuredChannels.contains(parentChannelId);
    }

    private static boolean containsDiscordRolePing(String text) {
        return text != null && DISCORD_ROLE_PING_PATTERN.matcher(text).find();
    }
}
