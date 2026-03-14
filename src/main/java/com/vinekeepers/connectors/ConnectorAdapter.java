package com.vinekeepers.connectors;

import com.vinekeepers.bot.BotDefinition;

import java.util.List;

/**
 * Contract for a connector adapter: register bots with the connector (e.g. per-bot
 * gateway/sender/default registration). Engine, sink, and action registration stay in Bootstrap.
 */
public interface ConnectorAdapter {

    /**
     * Register the given bots with this connector using the shared context.
     * Adapter performs only per-bot gateway/sender/default registration and starts event sources.
     */
    void registerBots(List<BotDefinition> bots, ConnectorContext context);
}
