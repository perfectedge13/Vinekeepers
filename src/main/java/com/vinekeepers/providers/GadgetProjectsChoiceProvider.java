package com.vinekeepers.providers;

import com.vinekeepers.events.Event;
import com.vinekeepers.gadget.GadgetProjectDefinition;
import com.vinekeepers.gadget.GadgetProjectRegistry;
import com.vinekeepers.interactions.ResponseIntent;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.DynamicChoiceProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Supplies Gadget deploy project choices from {@link GadgetProjectRegistry}.
 */
public final class GadgetProjectsChoiceProvider implements DynamicChoiceProvider {

    private final GadgetProjectRegistry registry;

    public GadgetProjectsChoiceProvider(GadgetProjectRegistry registry) {
        this.registry = registry != null ? registry : new GadgetProjectRegistry(List.of());
    }

    @Override
    public List<ResponseIntent.Choice> getChoices(Event event, ConfigurableWorkflowState state) {
        List<ResponseIntent.Choice> choices = new ArrayList<>();
        for (GadgetProjectDefinition p : registry.getProjects()) {
            choices.add(new ResponseIntent.Choice(p.getId(), p.getLabel(), null));
        }
        return choices;
    }
}
