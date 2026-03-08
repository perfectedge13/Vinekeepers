package com.vinekeepers.workflow;

import com.vinekeepers.events.Event;

/**
 * Resolves a state/session key for a bot and incoming event.
 */
@FunctionalInterface
public interface SessionKeyStrategy {

    String resolveSessionKey(String botId, Event event);
}
