package com.vinekeepers.providers;

import com.vinekeepers.devops.DeployTarget;
import com.vinekeepers.devops.DeployTargetRegistry;
import com.vinekeepers.events.Event;
import com.vinekeepers.interactions.ResponseIntent;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.DynamicChoiceProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Deploy target choices from {@link DeployTargetRegistry}.
 */
public final class DeployTargetsChoiceProvider implements DynamicChoiceProvider {

    private final DeployTargetRegistry registry;

    public DeployTargetsChoiceProvider(DeployTargetRegistry registry) {
        this.registry = registry != null ? registry : new DeployTargetRegistry(List.of());
    }

    @Override
    public List<ResponseIntent.Choice> getChoices(Event event, ConfigurableWorkflowState state) {
        List<ResponseIntent.Choice> choices = new ArrayList<>();
        for (DeployTarget t : registry.getTargets()) {
            choices.add(new ResponseIntent.Choice(t.getId(), t.getLabel(), null));
        }
        return choices;
    }
}
