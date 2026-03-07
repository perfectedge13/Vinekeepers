package com.vinekeepers.connectors;

import com.vinekeepers.events.EventBus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordEventSourceTest {

    @Test
    void startThenStopSetsRunningFlag() {
        DiscordEventSource source = new DiscordEventSource();
        assertFalse(source.isRunning());
        source.start(new EventBus());
        assertTrue(source.isRunning());
        source.stop();
        assertFalse(source.isRunning());
    }

    @Test
    void sendDoesNotThrow() {
        DiscordEventSource source = new DiscordEventSource();
        source.send("ch1", "msg1", "Hello");
        source.send("ch2", null, "No reply id");
    }
}
