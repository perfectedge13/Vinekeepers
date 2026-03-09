package com.vinekeepers.workflow;

import com.vinekeepers.events.Event;
import com.vinekeepers.interactions.ResponseIntent;

import java.util.List;

/**
 * Provides dynamic choices for prompt steps (e.g. repo list from GitHub).
 * Used when a workflow step has choiceProvider set instead of static choices.
 */
public interface DynamicChoiceProvider {

    /**
     * Returns choices for the current event and state (e.g. "Use last repo", GitHub repos, "Custom repo").
     * May return empty list on failure or when no choices are available; caller will fall back to plain prompt.
     */
    List<ResponseIntent.Choice> getChoices(Event event, ConfigurableWorkflowState state);
}
