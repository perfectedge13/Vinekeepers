package com.vinekeepers.connectors;

import java.util.Objects;

/**
 * Discord-specific options passed to the Discord adapter constructor.
 * Holds default token env key (from config root defaultDiscordTokenEnvKey, retained transitional).
 */
public final class DiscordConnectorConfig {

    private final String defaultTokenEnvKey;

    public DiscordConnectorConfig(String defaultTokenEnvKey) {
        this.defaultTokenEnvKey = (defaultTokenEnvKey != null && !defaultTokenEnvKey.isBlank())
                ? defaultTokenEnvKey.trim() : null;
    }

    public String getDefaultTokenEnvKey() {
        return defaultTokenEnvKey;
    }
}
