package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.devops.ComposeOperation;
import com.vinekeepers.devops.DeployTarget;
import com.vinekeepers.devops.DeployTargetRegistry;
import com.vinekeepers.devops.HostComposeOpsRunner;
import com.vinekeepers.events.Event;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Runs allowlisted Docker Compose operations for a deploy target; uses {@code __botId} for Discord delivery.
 */
public final class RunDeployComposeAction implements com.vinekeepers.workflow.WorkflowAction {

    private final OutboundDeliveryRouter router;
    private final DeployTargetRegistry targetRegistry;

    public RunDeployComposeAction(OutboundDeliveryRouter router, DeployTargetRegistry targetRegistry) {
        this.router = router;
        this.targetRegistry = targetRegistry != null ? targetRegistry : new DeployTargetRegistry(java.util.List.of());
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> args = merged(state, bind);
        String targetId = firstNonBlank(getString(args, "deployTargetId"), getString(args, "gadgetProject"));
        String workflowBotId = getString(args, "__botId");
        String parentChannelId = getString(args, "channelId");
        String threadId = getString(args, "deliveryChannelId");
        ComposeOperation op = ComposeOperation.parse(getString(args, "composeOperation"));
        String service = getString(args, "deployComposeService");
        if (op == ComposeOperation.PS && (service == null || service.isBlank())) {
            service = "_all";
        }

        if (targetId == null || targetId.isBlank()) {
            return fail("Missing deployTargetId for compose operation.");
        }
        if (workflowBotId == null || workflowBotId.isBlank()) {
            return fail("Missing __botId for compose operation.");
        }
        Optional<DeployTarget> t = targetRegistry.findById(targetId);
        if (t.isEmpty()) {
            return fail("Unknown deploy target: `" + targetId + "`.");
        }
        if (service == null || service.isBlank()) {
            return fail("Missing deployComposeService.");
        }

        String progressTarget = threadId;
        if (progressTarget == null
                || progressTarget.isBlank()
                || CreateThreadAction.THREAD_CREATE_FAILED.equals(progressTarget)) {
            progressTarget = parentChannelId;
        }
        if (progressTarget == null || progressTarget.isBlank()) {
            return fail("Missing channel for compose progress updates.");
        }

        HostComposeOpsRunner.submit(router, workflowBotId, progressTarget, t.get(), op, service);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deployComposeMessage", "Compose operation started. Progress in <#" + progressTarget + ">.");
        out.put("deployComposeTargetId", progressTarget);
        return out;
    }

    private static Object fail(String msg) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deployComposeMessage", msg);
        out.put("deployComposeTargetId", "");
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
