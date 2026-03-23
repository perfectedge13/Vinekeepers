package com.vinekeepers.connectors;

import com.vinekeepers.events.EventBus;
import com.vinekeepers.events.EventSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event source for GitHub/Git (stub only; webhook ingestion is not implemented yet).
 */
public class GitHubEventSource implements EventSource {

    private static final Logger log = LoggerFactory.getLogger(GitHubEventSource.class);

    private volatile boolean running;

    @Override
    public void start(EventBus bus) {
        running = true;
        log.info("GitHubEventSource started (stub; waiting for a real GitHub webhook/event bridge)");
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
