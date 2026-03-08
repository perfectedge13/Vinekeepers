package com.vinekeepers.connectors;

import com.vinekeepers.events.Event;

import java.util.function.Consumer;

/**
 * Transport layer for Discord messaging and events.
 */
public interface DiscordGateway {

    void connect(Consumer<Event> publisher);

    void shutdown();

    void send(String channelId, String messageId, String content);

    boolean isConnected();
}
