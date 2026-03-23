package com.vinekeepers.connectors;

import com.vinekeepers.events.Event;
import com.vinekeepers.events.EventBus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubEventSourceTest {

    @Test
    void startThenStopSetsRunningFlagWithoutPublishingStubEvent() {
        GitHubEventSource source = new GitHubEventSource();
        EventBus bus = new EventBus();
        List<Event> events = new ArrayList<>();
        bus.subscribe(events::add);

        assertFalse(source.isRunning());
        source.start(bus);
        assertTrue(source.isRunning());
        assertTrue(events.isEmpty(), "stub GitHub source must not publish placeholder startup events");

        source.stop();
        assertFalse(source.isRunning());
    }
}
