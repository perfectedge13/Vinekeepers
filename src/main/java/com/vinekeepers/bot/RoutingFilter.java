package com.vinekeepers.bot;

import java.util.Set;

/**
 * Routing filter configuration: Discord (authors, channels, channel exclusions, trigger, mention) and GitHub/Git.
 */
public final class RoutingFilter {

    // Discord
    private final Set<String> discordAuthors;
    private final Set<String> discordChannels;
    private final Set<String> discordChannelsExclude;
    private final String discordTrigger;
    private final String discordMention;

    // GitHub / Git
    private final Set<String> repos;
    private final Set<String> prLabels;
    private final Set<String> prAuthors;

    /**
     * Discord authors/channels/trigger/mention plus GitHub fields; no channel exclusions.
     */
    public RoutingFilter(
            Set<String> discordAuthors,
            Set<String> discordChannels,
            String discordTrigger,
            String discordMention,
            Set<String> repos,
            Set<String> prLabels,
            Set<String> prAuthors) {
        this(discordAuthors, discordChannels, Set.of(), discordTrigger, discordMention, repos, prLabels, prAuthors);
    }

    /**
     * Full Discord filter including optional {@code discordChannelsExclude} (matched channel ids never route to this rule).
     */
    public RoutingFilter(
            Set<String> discordAuthors,
            Set<String> discordChannels,
            Set<String> discordChannelsExclude,
            String discordTrigger,
            String discordMention,
            Set<String> repos,
            Set<String> prLabels,
            Set<String> prAuthors) {
        this.discordAuthors = discordAuthors == null ? Set.of() : Set.copyOf(discordAuthors);
        this.discordChannels = discordChannels == null ? Set.of() : Set.copyOf(discordChannels);
        this.discordChannelsExclude = discordChannelsExclude == null ? Set.of() : Set.copyOf(discordChannelsExclude);
        this.discordTrigger = discordTrigger;
        this.discordMention = discordMention;
        this.repos = repos == null ? Set.of() : Set.copyOf(repos);
        this.prLabels = prLabels == null ? Set.of() : Set.copyOf(prLabels);
        this.prAuthors = prAuthors == null ? Set.of() : Set.copyOf(prAuthors);
    }

    /** GitHub-only style constructor (no Discord mention). */
    public RoutingFilter(
            Set<String> discordAuthors,
            Set<String> discordChannels,
            String discordTrigger,
            Set<String> repos,
            Set<String> prLabels,
            Set<String> prAuthors) {
        this(discordAuthors, discordChannels, Set.of(), discordTrigger, null, repos, prLabels, prAuthors);
    }

    public Set<String> getDiscordAuthors() {
        return discordAuthors;
    }

    public Set<String> getDiscordChannels() {
        return discordChannels;
    }

    public Set<String> getDiscordChannelsExclude() {
        return discordChannelsExclude;
    }

    public String getDiscordTrigger() {
        return discordTrigger;
    }

    public String getDiscordMention() {
        return discordMention;
    }

    public Set<String> getRepos() {
        return repos;
    }

    public Set<String> getPrLabels() {
        return prLabels;
    }

    public Set<String> getPrAuthors() {
        return prAuthors;
    }
}
