package com.vinekeepers.bot;

import com.vinekeepers.events.Event;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only normalized view over an Event for routing and session decisions.
 */
public final class NormalizedEventContext {

    private static final Pattern DISPLAY_MENTION_PATTERN = Pattern.compile("(?<!\\S)@([A-Za-z0-9._-]+)");
    private static final Pattern DISCORD_ID_MENTION_PATTERN = Pattern.compile("<@!?([^>]+)>");

    private final String sourceType;
    private final String eventType;
    private final String actorId;
    private final String channelId;
    private final String threadId;
    private final String conversationId;
    private final String text;
    private final List<String> mentions;
    private final String repo;
    private final List<String> labels;
    private final Map<String, Object> metadata;

    private NormalizedEventContext(String sourceType, String eventType, String actorId, String channelId,
                                   String threadId, String conversationId, String text, List<String> mentions,
                                   String repo, List<String> labels, Map<String, Object> metadata) {
        this.sourceType = sourceType != null ? sourceType : "";
        this.eventType = eventType != null ? eventType : "";
        this.actorId = actorId;
        this.channelId = channelId;
        this.threadId = threadId;
        this.conversationId = conversationId;
        this.text = text;
        this.mentions = mentions != null ? List.copyOf(mentions) : List.of();
        this.repo = repo;
        this.labels = labels != null ? List.copyOf(labels) : List.of();
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public static NormalizedEventContext from(Event event) {
        if (event == null) {
            return new NormalizedEventContext("", "", null, null, null, null, null, List.of(), null, List.of(), Map.of());
        }
        Map<String, Object> payload = event.getPayload();
        String sourceId = event.getSourceId();
        String sourceType = sourceId != null && sourceId.contains(":")
                ? sourceId.substring(0, sourceId.indexOf(':'))
                : sourceId;
        String actorId = firstString(payload, "authorId", "author", "user");
        String channelId = firstString(payload, "channelId", "channel");
        String threadId = firstString(payload, "threadId", "thread");
        String conversationId = threadId != null ? threadId : channelId;
        String text = firstString(payload, "text", "content");
        List<String> mentions = mentionsFrom(payload, text);
        String repo = firstString(payload, "repo", "repository");
        List<String> labels = listOfStrings(payload.get("labels"));
        return new NormalizedEventContext(sourceType, event.getKind(), actorId, channelId, threadId,
                conversationId, text, mentions, repo, labels, payload);
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getEventType() {
        return eventType;
    }

    public String getActorId() {
        return actorId;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getThreadId() {
        return threadId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getText() {
        return text;
    }

    public List<String> getMentions() {
        return mentions;
    }

    public String getRepo() {
        return repo;
    }

    public List<String> getLabels() {
        return labels;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    private static String firstString(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value instanceof String text) {
                return text;
            }
        }
        return null;
    }

    private static List<String> listOfStrings(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return List.of();
    }

    private static List<String> mentionsFrom(Map<String, Object> payload, String text) {
        Set<String> mentions = new LinkedHashSet<>();
        mentions.addAll(normalizeTokens(listOfStrings(payload.get("mentions"))));
        if (text != null && !text.isBlank()) {
            collectMatches(mentions, DISPLAY_MENTION_PATTERN.matcher(text));
            collectMatches(mentions, DISCORD_ID_MENTION_PATTERN.matcher(text));
        }
        return List.copyOf(mentions);
    }

    private static void collectMatches(Set<String> mentions, Matcher matcher) {
        while (matcher.find()) {
            String token = normalizeToken(matcher.group(1));
            if (token != null) {
                mentions.add(token);
            }
        }
    }

    private static List<String> normalizeTokens(List<String> values) {
        return values.stream()
                .map(NormalizedEventContext::normalizeToken)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static String normalizeToken(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }
}
