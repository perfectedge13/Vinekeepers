package com.vinekeepers.bot;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thread-safe snapshot of loaded bots for workflow actions that need persona or definition lookup
 * without hardcoding bot ids.
 */
public final class BotCatalog {

    private volatile Map<String, BotDefinition> byId = Map.of();

    public void replaceAll(List<BotDefinition> bots) {
        if (bots == null || bots.isEmpty()) {
            byId = Map.of();
            return;
        }
        Map<String, BotDefinition> m = new HashMap<>();
        for (BotDefinition b : bots) {
            if (b != null && b.getId() != null && !b.getId().isBlank()) {
                m.put(b.getId().trim(), b);
            }
        }
        byId = Collections.unmodifiableMap(m);
    }

    public Optional<BotDefinition> find(String botId) {
        if (botId == null || botId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(botId.trim()));
    }

    /**
     * Persona display name for Discord copy, or the bot id when unknown / unnamed.
     */
    public String displayNameForBot(String botId) {
        if (botId == null || botId.isBlank()) {
            return "";
        }
        return find(botId.trim())
                .map(b -> b.getPersona().getName())
                .filter(n -> n != null && !n.isBlank())
                .orElse(botId.trim());
    }
}
