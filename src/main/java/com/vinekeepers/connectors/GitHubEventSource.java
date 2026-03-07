package com.vinekeepers.connectors;

import com.vinekeepers.events.Event;
import com.vinekeepers.events.EventBus;
import com.vinekeepers.events.EventSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Event source for GitHub/Git (stub: publishes placeholder events for PRs, etc.).
 */
public class GitHubEventSource implements EventSource {

    private static final Logger log = LoggerFactory.getLogger(GitHubEventSource.class);
    private static final String SOURCE_ID = "github:default";

    private volatile boolean running;

    @Override
    public void start(EventBus bus) {
        running = true;
        log.info("GitHubEventSource started (stub)");
        // Stub: no real GitHub webhooks yet
        bus.publish(new Event(SOURCE_ID, "pull_request", Map.of(
                "repository", "owner/repo",
                "author", "user",
                "labels", List.of("bug"))));
    }

    @Override
    public void stop() {
        running = false;
        log.info("GitHubEventSource stopped");
    }

    public boolean isRunning() {
        return running;
    }
}
