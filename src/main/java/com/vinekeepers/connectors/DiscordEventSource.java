package com.vinekeepers.connectors;

import com.vinekeepers.events.EventBus;
import com.vinekeepers.events.EventSource;
import com.vinekeepers.env.Env;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event source for Discord.
 */
public class DiscordEventSource implements EventSource, DiscordReplySender {

    private static final Logger log = LoggerFactory.getLogger(DiscordEventSource.class);

    private final DiscordGateway gateway;
    private volatile boolean running;

    public DiscordEventSource() {
        this(new JdaDiscordGateway(Env.get("DISCORD_BOT_TOKEN", "")));
    }

    /** For multi-bot: create a source with a specific gateway (e.g. per-bot token). */
    public DiscordEventSource(DiscordGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void start(EventBus bus) {
        gateway.connect(bus::publish);
        running = gateway.isConnected();
        log.info("DiscordEventSource started");
    }

    @Override
    public void stop() {
        gateway.shutdown();
        running = false;
        log.info("DiscordEventSource stopped");
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void send(String channelId, String messageId, String content) {
        gateway.send(channelId, messageId, content);
    }

    /**
     * Reply sink implementing the connector contract (lifecycle operations).
     * Register with engine via registerSink("discord", getReplySink()).
     */
    public DiscordAppReplySink getReplySink() {
        return new DiscordAppReplySink(gateway);
    }

    /**
     * Gateway for lifecycle actions (e.g. createTextChannel). Used by Bootstrap when wiring Discord-backed workflow actions.
     */
    public DiscordGateway getGateway() {
        return gateway;
    }
}
