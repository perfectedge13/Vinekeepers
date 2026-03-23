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
    /** User/bot mentions only; excludes Discord role pings ({@literal <@&id>}). */
    private static final Pattern DISCORD_USER_OR_BOT_MENTION_PATTERN = Pattern.compile("<@!?([0-9]+)>");

    private final String sourceType;
    private final String eventType;
    private final String actorId;
    private final String actorUsername;
    private final String channelId;
    private final String parentChannelId;
    private final String threadId;
    private final String conversationId;
    private final String text;
    private final List<String> mentions;
    private final String repo;
    private final List<String> labels;
    private final Map<String, Object> metadata;
    private final String interactionId;
    private final String token;
    private final String customId;
    private final List<String> interactionValues;

    private NormalizedEventContext(String sourceType, String eventType, String actorId, String actorUsername, String channelId,
                                   String parentChannelId, String threadId, String conversationId, String text, List<String> mentions,
                                   String repo, List<String> labels, Map<String, Object> metadata,
                                   String interactionId, String token, String customId, List<String> interactionValues) {
        this.sourceType = sourceType != null ? sourceType : "";
        this.eventType = eventType != null ? eventType : "";
        this.actorId = actorId;
        this.actorUsername = actorUsername;
        this.channelId = channelId;
        this.parentChannelId = parentChannelId;
        this.threadId = threadId;
        this.conversationId = conversationId;
        this.text = text;
        this.mentions = mentions != null ? List.copyOf(mentions) : List.of();
        this.repo = repo;
        this.labels = labels != null ? List.copyOf(labels) : List.of();
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        this.interactionId = interactionId;
        this.token = token;
        this.customId = customId;
        this.interactionValues = interactionValues != null ? List.copyOf(interactionValues) : List.of();
    }

    public static NormalizedEventContext from(Event event) {
        if (event == null) {
            return new NormalizedEventContext("", "", null, null, null, null, null, null, null, List.of(), null, List.of(), Map.of(), null, null, null, List.of());
        }
        Map<String, Object> payload = event.getPayload();
        String sourceId = event.getSourceId();
        String sourceType = sourceId != null && sourceId.contains(":")
                ? sourceId.substring(0, sourceId.indexOf(':'))
                : sourceId;
        String actorId = firstString(payload, "authorId", "user");
        String actorUsername = firstString(payload, "author");
        String channelId = firstString(payload, "channelId", "channel");
        String parentChannelId = firstString(payload, "parentChannelId");
        String threadId = firstString(payload, "threadId", "thread");
        String conversationId = threadId != null ? threadId : channelId;
        String text = firstString(payload, "text", "content");
        List<String> mentions = mentionsFrom(payload, text);
        String repo = firstString(payload, "repo", "repository");
        List<String> labels = listOfStrings(payload.get("labels"));
        String interactionId = firstString(payload, "interactionId");
        String token = firstString(payload, "token");
        String customId = firstString(payload, "customId");
        List<String> interactionValues = "interaction".equals(event.getKind())
                ? listOfStrings(payload.get("values") != null ? extractValuesList(payload.get("values")) : null)
                : List.of();
        return new NormalizedEventContext(sourceType, event.getKind(), actorId, actorUsername, channelId, parentChannelId, threadId,
                conversationId, text, mentions, repo, labels, payload,
                interactionId, token, customId, interactionValues);
    }

    private static Object extractValuesList(Object values) {
        if (values instanceof Map<?, ?> m && m.containsKey("values")) {
            return m.get("values");
        }
        return values;
    }

    /**
     * Returns the list of selected values from an interaction payload (e.g. Discord select menu).
     * Unwraps payload.values when it is a Map with key "values" so both gateway shapes are supported.
     */
    public static List<String> getInteractionValuesFromPayload(Map<String, Object> payload) {
        if (payload == null) return List.of();
        Object raw = payload.get("values");
        Object unwrapped = raw != null ? extractValuesList(raw) : null;
        return listOfStrings(unwrapped);
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

    public String getActorUsername() {
        return actorUsername;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getParentChannelId() {
        return parentChannelId;
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

    public String getInteractionId() {
        return interactionId;
    }

    public String getToken() {
        return token;
    }

    public String getCustomId() {
        return customId;
    }

    public List<String> getInteractionValues() {
        return interactionValues;
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
            collectMatches(mentions, DISCORD_USER_OR_BOT_MENTION_PATTERN.matcher(text));
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
