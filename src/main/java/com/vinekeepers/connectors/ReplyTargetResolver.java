package com.vinekeepers.connectors;

import com.vinekeepers.events.Event;
import com.vinekeepers.interactions.ReplyTarget;

import java.util.Optional;

/**
 * Resolves where to send a reply for an event. Connector-owned; engine uses by connector id.
 */
public interface ReplyTargetResolver {

    /**
     * Resolves the reply target for the given event.
     *
     * @param event the event to resolve a target for
     * @return the reply target, or empty if this connector cannot resolve one for the event
     */
    Optional<ReplyTarget> resolve(Event event);
}
