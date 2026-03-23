package com.vinekeepers.state;

import java.util.Locale;
import java.util.Optional;

/**
 * State-store keys and helpers scoped by workflow bot id (no product-specific bot names in key shapes).
 */
public final class BotScopedStateKeys {

    private static final String LEGACY_LUNA_LAST_REPO_PREFIX = "luna:lastRepo:";
    private static final String LAST_REPO_PREFIX = "vk:lastRepo:";

    private BotScopedStateKeys() {
    }

    public static String lastRepoKey(String botId, String authorId) {
        if (authorId == null || authorId.isBlank()) {
            return LAST_REPO_PREFIX + ":";
        }
        String b = sanitizeBotIdSegment(botId);
        return LAST_REPO_PREFIX + b + ":" + authorId.trim();
    }

    public static String legacyLunaLastRepoKey(String authorId) {
        if (authorId == null) {
            return LEGACY_LUNA_LAST_REPO_PREFIX;
        }
        return LEGACY_LUNA_LAST_REPO_PREFIX + authorId.trim();
    }

    /**
     * Read last repo: prefer bot-scoped key, then legacy {@code luna:lastRepo:} for migration.
     */
    public static Optional<String> resolveLastRepo(StateStore store, String botId, String authorId) {
        if (store == null || authorId == null || authorId.isBlank()) {
            return Optional.empty();
        }
        if (botId != null && !botId.isBlank()) {
            Optional<String> scoped = store.get(lastRepoKey(botId, authorId), String.class);
            if (scoped.isPresent() && !scoped.get().isBlank()) {
                return scoped;
            }
        }
        return store.get(legacyLunaLastRepoKey(authorId), String.class)
                .filter(s -> !s.isBlank());
    }

    /**
     * Prefix for Cursor agent branch names ({@code prefix/slug-timestamp}).
     */
    public static String branchPrefixForBot(String botId) {
        String s = sanitizeBotIdSegment(botId);
        return s.isBlank() ? "vk" : s;
    }

    private static String sanitizeBotIdSegment(String botId) {
        if (botId == null || botId.isBlank()) {
            return "";
        }
        String s = botId.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (s.length() > 32) {
            s = s.substring(0, 32).replaceAll("-+$", "");
        }
        return s;
    }
}
