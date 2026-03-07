package com.vinekeepers.events;

/**
 * Source of events; pushes events to the given EventBus.
 */
public interface EventSource {

    /**
     * Start producing events and publishing them to the bus.
     */
    void start(EventBus bus);

    /**
     * Stop producing events.
     */
    void stop();
}
