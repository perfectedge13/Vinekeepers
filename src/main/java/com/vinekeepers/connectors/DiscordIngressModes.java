package com.vinekeepers.connectors;

import com.vinekeepers.bot.BotDefinition;
import com.vinekeepers.bot.ConnectorIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Discord ingress policy: independent message vs interaction publication modes.
 * Derived from YAML {@code identities.discord.ingress} when present; otherwise from routing + handlesOwnedSpaces.
 */
public final class DiscordIngressModes {

    private static final Logger log = LoggerFactory.getLogger(DiscordIngressModes.class);

    private final DiscordMessageIngressMode messageMode;
    private final DiscordInteractionIngressMode interactionMode;

    public DiscordIngressModes(DiscordMessageIngressMode messageMode, DiscordInteractionIngressMode interactionMode) {
        this.messageMode = Objects.requireNonNull(messageMode, "messageMode");
        this.interactionMode = Objects.requireNonNull(interactionMode, "interactionMode");
    }

    public DiscordMessageIngressMode messageMode() {
        return messageMode;
    }

    public DiscordInteractionIngressMode interactionMode() {
        return interactionMode;
    }

    /** Both listeners off — outbound-only gateway. */
    public boolean isOutboundOnly() {
        return messageMode == DiscordMessageIngressMode.NONE
                && interactionMode == DiscordInteractionIngressMode.NONE;
    }

    /** True when {@link DiscordOwnedSpacePredicate} is required for filtering. */
    public boolean needsOwnedSpacePredicate() {
        return messageMode == DiscordMessageIngressMode.OWNED_SPACES
                || interactionMode == DiscordInteractionIngressMode.OWNED_SPACES;
    }

    /**
     * When stores are unavailable, drop OWNED_SPACES filtering: no broad messages, interactions without channel filter.
     */
    public DiscordIngressModes fallbackWithoutStores() {
        DiscordMessageIngressMode m = messageMode == DiscordMessageIngressMode.OWNED_SPACES
                ? DiscordMessageIngressMode.NONE : messageMode;
        DiscordInteractionIngressMode i = interactionMode == DiscordInteractionIngressMode.OWNED_SPACES
                ? DiscordInteractionIngressMode.OWN_MESSAGES : interactionMode;
        return new DiscordIngressModes(m, i);
    }

    /** Luna-style: all messages and interactions (subject to Router filters). */
    public static DiscordIngressModes routedFull() {
        return new DiscordIngressModes(DiscordMessageIngressMode.ROUTED, DiscordInteractionIngressMode.ROUTED);
    }

    /** Default shared-token gateway: behave like full routed ingress. */
    public static DiscordIngressModes defaultSharedGateway() {
        return routedFull();
    }

    public static DiscordIngressModes resolve(BotDefinition bot, Set<String> routedBotIds) {
        if (bot == null) {
            return new DiscordIngressModes(DiscordMessageIngressMode.NONE, DiscordInteractionIngressMode.NONE);
        }
        Set<String> routed = routedBotIds != null ? routedBotIds : Set.of();
        boolean isRouted = routed.contains(bot.getId());
        Optional<ConnectorIdentity> discord = bot.getConnectorIdentity("discord");
        boolean handlesOwned = discord.map(d -> d.getBoolean("handlesOwnedSpaces")).orElse(false);

        DiscordMessageIngressMode dm;
        DiscordInteractionIngressMode di;
        if (isRouted && handlesOwned) {
            dm = DiscordMessageIngressMode.ROUTED;
            di = DiscordInteractionIngressMode.ROUTED;
        } else if (isRouted) {
            dm = DiscordMessageIngressMode.ROUTED;
            di = DiscordInteractionIngressMode.ROUTED;
        } else if (handlesOwned) {
            dm = DiscordMessageIngressMode.OWNED_SPACES;
            di = DiscordInteractionIngressMode.OWN_MESSAGES;
        } else {
            dm = DiscordMessageIngressMode.NONE;
            di = DiscordInteractionIngressMode.NONE;
        }

        if (discord.isEmpty()) {
            return new DiscordIngressModes(dm, di);
        }

        Map<String, Object> ingressMap = readIngressMap(discord.get());
        if (ingressMap == null || ingressMap.isEmpty()) {
            return new DiscordIngressModes(dm, di);
        }

        DiscordMessageIngressMode parsedM = parseMessage(ingressMap.get("messages"), dm);
        DiscordInteractionIngressMode parsedI = parseInteraction(ingressMap.get("interactions"), di);
        return new DiscordIngressModes(parsedM, parsedI);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readIngressMap(ConnectorIdentity identity) {
        Object raw = identity.getAttribute("ingress");
        if (raw instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return null;
    }

    private static DiscordMessageIngressMode parseMessage(Object raw, DiscordMessageIngressMode derivedDefault) {
        if (raw == null) {
            return derivedDefault;
        }
        String s = raw.toString().trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return derivedDefault;
        }
        try {
            return DiscordMessageIngressMode.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown identities.discord.ingress.messages value '{}'; using derived default {}", raw, derivedDefault);
            return derivedDefault;
        }
    }

    private static DiscordInteractionIngressMode parseInteraction(Object raw, DiscordInteractionIngressMode derivedDefault) {
        if (raw == null) {
            return derivedDefault;
        }
        String s = raw.toString().trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return derivedDefault;
        }
        try {
            return DiscordInteractionIngressMode.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown identities.discord.ingress.interactions value '{}'; using derived default {}", raw, derivedDefault);
            return derivedDefault;
        }
    }
}
