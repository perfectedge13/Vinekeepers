package com.vinekeepers.workflow.actions;

import com.vinekeepers.env.Env;
import com.vinekeepers.events.Event;
import com.vinekeepers.gadget.GadgetProjectDefinition;
import com.vinekeepers.gadget.GadgetProjectRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sets {@code gadgetProject} when the manifest has a single entry or {@code DEPLOY_DEFAULT_PROJECT} /
 * {@code GADGET_DEFAULT_PROJECT} matches; sets {@code deployNeedsProjectPick} for workflow branching.
 */
public final class DeployResolveProjectAction implements com.vinekeepers.workflow.WorkflowAction {

    private final GadgetProjectRegistry registry;

    public DeployResolveProjectAction(GadgetProjectRegistry registry) {
        this.registry = registry != null ? registry : new GadgetProjectRegistry(List.of());
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<GadgetProjectDefinition> projects = registry.getProjects();
        if (projects.size() == 1) {
            out.put("gadgetProject", projects.get(0).getId());
            out.put("deployNeedsProjectPick", "false");
            return out;
        }
        String def = firstNonBlank(Env.get("DEPLOY_DEFAULT_PROJECT", ""), Env.get("GADGET_DEFAULT_PROJECT", ""));
        if (!def.isBlank() && registry.findById(def).isPresent()) {
            out.put("gadgetProject", def.trim());
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
