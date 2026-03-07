package com.vinekeepers.bot;

import com.vinekeepers.events.Event;

import java.util.ArrayList;
import java.util.List;
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
        String sourceId = event.getSourceId();
        String kind = event.getKind();
        var payload = event.getPayload();

        if (sourceId.startsWith("discord:")) {
            if (!f.getDiscordAuthors().isEmpty()) {
                String author = event.getPayload("authorId", String.class);
                if (author == null) author = (String) payload.get("author");
                if (author == null || !f.getDiscordAuthors().contains(author)) return false;
            }
            if (!f.getDiscordChannels().isEmpty()) {
                String channel = event.getPayload("channelId", String.class);
                if (channel == null) channel = (String) payload.get("channel");
                if (channel == null || !f.getDiscordChannels().contains(channel)) return false;
            }
            if (f.getDiscordTrigger() != null && !f.getDiscordTrigger().isEmpty()) {
                String text = event.getPayload("text", String.class);
                if (text == null) text = (String) payload.get("content");
                if (text == null || !text.contains(f.getDiscordTrigger())) return false;
            }
            return true;
        }

        if (sourceId.startsWith("github:") || kind != null && kind.toLowerCase().contains("pr")) {
            if (!f.getRepos().isEmpty()) {
                String repo = event.getPayload("repo", String.class);
                if (repo == null) repo = (String) payload.get("repository");
                if (repo == null || !f.getRepos().contains(repo)) return false;
            }
            if (!f.getPrLabels().isEmpty()) {
                @SuppressWarnings("unchecked")
                List<String> labels = event.getPayload("labels", List.class);
                if (labels == null || labels.stream().noneMatch(f.getPrLabels()::contains)) return false;
            }
            if (!f.getPrAuthors().isEmpty()) {
                String author = event.getPayload("author", String.class);
                if (author == null) author = event.getPayload("user", String.class);
                if (author == null || !f.getPrAuthors().contains(author)) return false;
            }
            return true;
        }

        return true;
    }
}
