package com.vinekeepers.bot;

import com.vinekeepers.events.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Routes events to bot ids using event filters (from Routing).
 */
public final class Router {

    private final List<Routing> routings = new ArrayList<>();

    public void addRouting(Routing routing) {
        routings.add(Objects.requireNonNull(routing));
    }

    public void clear() {
        routings.clear();
    }

    /**
     * Returns the list of bot ids that should handle this event (first matching routing wins, or all if multiple).
     * For simplicity we return all matching bot ids.
     */
    public List<String> route(Event event) {
        List<String> botIds = new ArrayList<>();
        for (Routing r : routings) {
            if (matches(r.getFilter(), event)) {
                botIds.add(r.getBotId());
            }
        }
        return botIds;
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
                boolean authorMatch = (actorId != null && f.getDiscordAuthors().contains(actorId))
                        || (usernameNorm != null && !usernameNorm.isEmpty() && f.getDiscordAuthors().stream()
                                .anyMatch(a -> a != null && a.trim().toLowerCase(Locale.ROOT).equals(usernameNorm)));
                if (!authorMatch) return false;
            }
            if (!f.getDiscordChannels().isEmpty()) {
                String channel = context.getChannelId();
                if (channel == null || !f.getDiscordChannels().contains(channel)) return false;
            }
            if (f.getDiscordTrigger() != null && !f.getDiscordTrigger().isEmpty()) {
                String text = context.getText();
                if (text == null || !text.contains(f.getDiscordTrigger())) return false;
            }
            if (f.getDiscordMention() != null && !f.getDiscordMention().isBlank()) {
                String mention = f.getDiscordMention().trim().toLowerCase(Locale.ROOT);
                if (!context.getMentions().contains(mention)) return false;
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
