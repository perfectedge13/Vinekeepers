package com.vinekeepers.bot;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Routing filter configuration: Discord (authors, channels, trigger) and GitHub/Git (repos, prLabels, prAuthors).
 */
public final class RoutingFilter {

    // Discord
    private final Set<String> discordAuthors;
    private final Set<String> discordChannels;
    private final String discordTrigger;

    // GitHub / Git
    private final Set<String> repos;
    private final Set<String> prLabels;
    private final Set<String> prAuthors;

    public RoutingFilter(
            Set<String> discordAuthors,
            Set<String> discordChannels,
            String discordTrigger,
            Set<String> repos,
            Set<String> prLabels,
            Set<String> prAuthors) {
        this.discordAuthors = discordAuthors == null ? Set.of() : Set.copyOf(discordAuthors);
        this.discordChannels = discordChannels == null ? Set.of() : Set.copyOf(discordChannels);
        this.discordTrigger = discordTrigger;
        this.repos = repos == null ? Set.of() : Set.copyOf(repos);
        this.prLabels = prLabels == null ? Set.of() : Set.copyOf(prLabels);
        this.prAuthors = prAuthors == null ? Set.of() : Set.copyOf(prAuthors);
    }

    public Set<String> getDiscordAuthors() {
        return discordAuthors;
    }

    public Set<String> getDiscordChannels() {
        return discordChannels;
    }

    public String getDiscordTrigger() {
        return discordTrigger;
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
