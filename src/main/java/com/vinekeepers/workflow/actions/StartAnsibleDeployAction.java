package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.devops.AnsiblePlaybookDeployRunner;
import com.vinekeepers.devops.DeployTarget;
import com.vinekeepers.devops.DeployTargetRegistry;
import com.vinekeepers.events.Event;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Starts an asynchronous Ansible playbook deploy; progress posts use the workflow bot id ({@code __botId}).
 */
public final class StartAnsibleDeployAction implements com.vinekeepers.workflow.WorkflowAction {

    private final OutboundDeliveryRouter router;
    private final DeployTargetRegistry targetRegistry;

    public StartAnsibleDeployAction(OutboundDeliveryRouter router, DeployTargetRegistry targetRegistry) {
        this.router = router;
        this.targetRegistry = targetRegistry != null ? targetRegistry : new DeployTargetRegistry(java.util.List.of());
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> args = merged(state, bind);
        String targetId = firstNonBlank(getString(args, "deployTargetId"), getString(args, "gadgetProject"));
        String branch = getString(args, "deployBranch");
        String parentChannelId = getString(args, "channelId");
        String threadId = getString(args, "deliveryChannelId");
        String workflowBotId = getString(args, "__botId");

        if (targetId == null || targetId.isBlank()) {
            return fail("Missing deployTargetId for Ansible deploy.");
        }
        if (workflowBotId == null || workflowBotId.isBlank()) {
            return fail("Missing __botId for Ansible deploy (workflow bot context).");
        }
        Optional<DeployTarget> t = targetRegistry.findById(targetId);
        if (t.isEmpty()) {
            return fail("Unknown deploy target: `" + targetId + "`.");
        }
        if (!t.get().hasAnsiblePlaybook()) {
            return fail("Target `" + targetId + "` has no Ansible playbook configured.");
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

        AnsiblePlaybookDeployRunner.submit(router, workflowBotId, progressTarget, t.get(), branch);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deployAnsibleMessage", "Ansible deploy started. Progress in <#" + progressTarget + ">.");
        out.put("deployAnsibleTargetId", progressTarget);
        out.put("gadgetDeployMessage", out.get("deployAnsibleMessage"));
        out.put("gadgetDeployTargetId", progressTarget);
        return out;
    }

    private static Object fail(String msg) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deployAnsibleMessage", msg);
        out.put("deployAnsibleTargetId", "");
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

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        return b != null && !b.isBlank() ? b.trim() : null;
    }
}
