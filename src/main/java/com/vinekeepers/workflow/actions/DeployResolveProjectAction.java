package com.vinekeepers.workflow.actions;

import com.vinekeepers.devops.DeployTargetRegistry;
import com.vinekeepers.env.Env;
import com.vinekeepers.events.Event;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sets {@code deployTargetId} when the manifest has a single entry or default env matches; sets
 * {@code deployNeedsProjectPick} for workflow branching.
 */
public final class DeployResolveProjectAction implements com.vinekeepers.workflow.WorkflowAction {

    private final DeployTargetRegistry registry;

    public DeployResolveProjectAction(DeployTargetRegistry registry) {
        this.registry = registry != null ? registry : new DeployTargetRegistry(List.of());
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> out = new LinkedHashMap<>();
        var targets = registry.getTargets();
        if (targets.size() == 1) {
            out.put("deployTargetId", targets.get(0).getId());
            out.put("deployNeedsProjectPick", "false");
            return out;
        }
        String def = firstNonBlank(Env.get("DEPLOY_DEFAULT_PROJECT", ""), Env.get("GADGET_DEFAULT_PROJECT", ""));
        if (!def.isBlank() && registry.findById(def).isPresent()) {
            out.put("deployTargetId", def.trim());
            out.put("deployNeedsProjectPick", "false");
            return out;
        }
        out.put("deployNeedsProjectPick", "true");
        return out;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        return b != null ? b.trim() : "";
    }
}
