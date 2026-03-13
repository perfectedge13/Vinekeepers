package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.LifecycleContext;
import com.vinekeepers.state.LifecycleContextStore;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Workflow action: create a lifecycle context for a channel. Bind/state: channelId, configuredBotId,
 * runtimeBotInstanceId (or instanceId from state), optional repo (or project from state), requestText (or codeChange from state).
 * Precedence: bind wins over state for all fields (e.g. configuredBotId from step bind overrides state).
 * Returns context id. Context is indexed by channelId (externalRunId set later by launch_cursor_run).
 */
public final class CreateLifecycleContextAction implements com.vinekeepers.workflow.WorkflowAction {

    private final LifecycleContextStore lifecycleContextStore;

    public CreateLifecycleContextAction(LifecycleContextStore lifecycleContextStore) {
        this.lifecycleContextStore = lifecycleContextStore;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        if (lifecycleContextStore == null) {
            return "Lifecycle context store not available.";
        }
        String channelId = firstNonBlank(getString(bind, "channelId"), state != null ? getString(state, "channelId") : null);
        if (channelId == null || channelId.isBlank()) {
            return "Missing channelId for create_lifecycle_context.";
        }
        // Bind explicitly wins over state (step bind overrides state for configuredBotId and others)
        String configuredBotId = firstNonBlank(getString(bind, "configuredBotId"), state != null ? getString(state, "configuredBotId") : null);
        String runtimeBotInstanceId = firstNonBlank(getString(bind, "runtimeBotInstanceId"), state != null ? getString(state, "instanceId") : null);
        String repo = firstNonBlank(getString(bind, "repo"), state != null ? getString(state, "project") : null);
        String requestText = firstNonBlank(getString(bind, "requestText"), state != null ? getString(state, "codeChange") : null);
        String status = firstNonBlank(getString(bind, "status"), state != null ? getString(state, "status") : null);
        if (status == null || status.isBlank()) status = "provisioning";
        String deliveryChannelIdRaw = firstNonBlank(getString(bind, "deliveryChannelId"), state != null ? getString(state, "deliveryChannelId") : null);
        String deliveryChannelId = (deliveryChannelIdRaw != null && !deliveryChannelIdRaw.isBlank() && !CreateThreadAction.THREAD_CREATE_FAILED.equals(deliveryChannelIdRaw))
                ? deliveryChannelIdRaw : null;
        String contextId = "ctx-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        LifecycleContext context = new LifecycleContext(
                contextId, channelId, Instant.now(),
                null, configuredBotId, runtimeBotInstanceId, repo, requestText, status, deliveryChannelId);
        lifecycleContextStore.put(context);
        return contextId;
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }
}
