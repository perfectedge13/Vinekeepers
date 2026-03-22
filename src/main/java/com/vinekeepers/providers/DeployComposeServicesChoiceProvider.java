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
 * Allowlisted Compose service names for the selected {@code deployTargetId} (plus {@code _all}).
 */
public final class DeployComposeServicesChoiceProvider implements DynamicChoiceProvider {

    private final DeployTargetRegistry registry;

    public DeployComposeServicesChoiceProvider(DeployTargetRegistry registry) {
        this.registry = registry != null ? registry : new DeployTargetRegistry(List.of());
    }

    @Override
    public List<ResponseIntent.Choice> getChoices(Event event, ConfigurableWorkflowState state) {
        String targetId = stringFromState(state, "deployTargetId");
        if (targetId == null || targetId.isBlank()) {
            targetId = stringFromState(state, "gadgetProject");
        }
        if (targetId == null || targetId.isBlank()) {
            return List.of();
        }
        return registry.findById(targetId)
                .map(this::choicesForTarget)
                .orElseGet(List::of);
    }

    private List<ResponseIntent.Choice> choicesForTarget(DeployTarget target) {
        if (!target.getCompose().isConfigured()) {
            return List.of();
        }
        List<ResponseIntent.Choice> out = new ArrayList<>();
        out.add(new ResponseIntent.Choice("_all", "All configured services", null));
        for (String s : target.getCompose().allServiceNames()) {
            out.add(new ResponseIntent.Choice(s, s, null));
        }
        return out;
    }

    private static String stringFromState(ConfigurableWorkflowState state, String key) {
        if (state == null || key == null) {
            return null;
        }
        Object v = state.get(key);
        return v != null ? v.toString().trim() : null;
    }
}
