package com.vinekeepers.bot;

import com.vinekeepers.events.Event;

/**
 * Filter for events (e.g. by source, kind, or payload attributes).
 */
@FunctionalInterface
public interface EventFilter {

    boolean matches(Event event);
}
