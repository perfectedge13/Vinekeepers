package com.vinekeepers.connectors.openai;

import com.vinekeepers.events.Event;

import java.util.Map;

/**
 * Optional per-call metadata for planning OpenAI traffic: plain-English activity line for Discord and workflow state
 * for channel resolution.
 */
public record OpenAiCallContext(String activitySummary, Event event, Map<String, Object> workflowState) {

    public static OpenAiCallContext planning(Event event, Map<String, Object> workflowState, String activitySummary) {
        return new OpenAiCallContext(activitySummary, event, workflowState);
    }
}
