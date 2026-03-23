package com.vinekeepers.connectors.openai;

import com.vinekeepers.events.Event;

import java.util.Map;

/**
 * Publishes a short user-facing line when a planning OpenAI call starts (e.g. Discord channel post).
 */
@FunctionalInterface
public interface OpenAiPlanningProgressSink {

    OpenAiPlanningProgressSink NOOP = (event, workflowState, userFacingLine) -> {};

    void publish(Event event, Map<String, Object> workflowState, String userFacingLine);
}
