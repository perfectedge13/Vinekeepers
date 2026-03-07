package com.vinekeepers.connectors;

import com.vinekeepers.events.Event;
import com.vinekeepers.events.EventBus;
import com.vinekeepers.events.EventSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Event source for Discord (stub: publishes placeholder events for wiring).
 * Implements DiscordReplySender so the engine can send replies back to Discord.
 */
public class DiscordEventSource implements EventSource, DiscordReplySender {

    private static final Logger log = LoggerFactory.getLogger(DiscordEventSource.class);
    private static final String SOURCE_ID = "discord:default";

    private volatile boolean running;

    @Override
    public void start(EventBus bus) {
        running = true;
        log.info("DiscordEventSource started (stub)");
        // Stub: no real Discord API yet; could publish a bootstrap event for testing
        bus.publish(new Event(SOURCE_ID, "message", Map.of("content", "stub", "channelId", "stub", "authorId", "stub")));
    }

    @Override
    public void stop() {
        running = false;
        log.info("DiscordEventSource stopped");
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void send(String channelId, String messageId, String content) {
        log.info("Discord reply (stub): channelId={} messageId={} content={}", channelId, messageId, content != null ? content.substring(0, Math.min(80, content.length())) + (content.length() > 80 ? "..." : "") : "");
        // TODO: integrate real Discord API to send message to channel or reply to messageId
    }
}
