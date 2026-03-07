package com.vinekeepers.events;

/**
 * Subscriber that receives events from the EventBus.
 */
@FunctionalInterface
public interface EventSubscriber {

    void onEvent(Event event);
}
