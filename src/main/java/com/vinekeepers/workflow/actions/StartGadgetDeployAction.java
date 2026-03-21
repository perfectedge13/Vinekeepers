package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.events.Event;
import com.vinekeepers.gadget.GadgetDeployRunner;
import com.vinekeepers.gadget.GadgetProjectDefinition;
import com.vinekeepers.gadget.GadgetProjectRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Starts an asynchronous Ansible-backed deploy and posts initial status to the deploy thread (or parent channel).
 */
public final class StartGadgetDeployAction implements com.vinekeepers.workflow.WorkflowAction {

    private final OutboundDeliveryRouter router;
    private final GadgetProjectRegistry projectRegistry;
    private final String gadgetBotId;

    public StartGadgetDeployAction(OutboundDeliveryRouter router,
                                   GadgetProjectRegistry projectRegistry,
                                   String gadgetBotId) {
        this.router = router;
        this.projectRegistry = projectRegistry != null ? projectRegistry : new GadgetProjectRegistry(java.util.List.of());
        this.gadgetBotId = gadgetBotId != null ? gadgetBotId : "gadget";
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> args = merged(state, bind);
        String projectId = getString(args, "gadgetProject");
        String branch = getString(args, "deployBranch");
        String parentChannelId = getString(args, "channelId");
        String threadId = getString(args, "deliveryChannelId");

        if (projectId == null || projectId.isBlank()) {
            return fail("Missing gadgetProject for deploy.");
        }
        Optional<GadgetProjectDefinition> proj = projectRegistry.findById(projectId);
        if (proj.isEmpty()) {
            return fail("Unknown project: `" + projectId + "`.");
        }

        String progressTarget = threadId;
        if (progressTarget == null
                || progressTarget.isBlank()
                || CreateThreadAction.THREAD_CREATE_FAILED.equals(progressTarget)) {
            progressTarget = parentChannelId;
        }
        if (progressTarget == null || progressTarget.isBlank()) {
            return fail("Missing channel for deploy progress updates.");
        }

        GadgetDeployRunner.submit(router, gadgetBotId, progressTarget, proj.get(), branch);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("gadgetDeployMessage", "Deploy started. Progress in <#" + progressTarget + ">.");
        out.put("gadgetDeployTargetId", progressTarget);
        return out;
    }

    private static Object fail(String msg) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("gadgetDeployMessage", msg);
        out.put("gadgetDeployTargetId", "");
        return out;
    }

    private static Map<String, Object> merged(Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (state != null) {
            m.putAll(state);
        }
        if (bind != null) {
            m.putAll(bind);
        }
        return m;
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
