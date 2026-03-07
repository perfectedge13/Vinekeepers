package com.vinekeepers.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central bus for publishing and subscribing to events.
 */
public class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    private final List<EventSubscriber> subscribers = new CopyOnWriteArrayList<>();

    public void subscribe(EventSubscriber subscriber) {
        subscribers.add(subscriber);
    }

    public void unsubscribe(EventSubscriber subscriber) {
        subscribers.remove(subscriber);
    }

    public void publish(Event event) {
        log.debug("Publishing event: sourceId={}, kind={}", event.getSourceId(), event.getKind());
        for (EventSubscriber s : subscribers) {
            try {
                s.onEvent(event);
            } catch (Exception e) {
                log.warn("Subscriber threw on event: {}", e.getMessage());
            }
        }
    }
}
