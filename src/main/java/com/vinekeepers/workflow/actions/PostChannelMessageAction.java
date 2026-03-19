package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.connectors.ReplySender;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.PlanningRole;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Workflow action: state-driven post to a channel (e.g. lifecycle room). Bind/state: channelId, content.
 * Interpolation uses a merged map (state then bind); bind overrides state, so {{lifecycleBotName}} from bind wins.
 * Optional bind/state: asBotId or asRole (ORCHESTRATOR, ARCHITECT, AUDITOR, SCRIBE). Sender precedence:
 * asBotId over asRole over default ReplySender path. Explicit asBotId/asRole requires OutboundDeliveryRouter
 * and strict resolution (errors returned when sender or role cannot be resolved; no silent fallback).
 * Optional bind/state: target (room | thread) or targetChannelId for explicit send target:
 * - If targetChannelId present and non-blank, use as send target.
 * - If target is "room", use channelId.
 * - If target is "thread", use deliveryChannelId (THREAD_CREATE_FAILED fallback to channelId).
 * - Else legacy: firstNonBlank(deliveryChannelId, channelId).
 */
public final class PostChannelMessageAction implements com.vinekeepers.workflow.WorkflowAction {

    private final ReplySender replySender;
    private final OutboundDeliveryRouter outboundDeliveryRouter;

    public PostChannelMessageAction(ReplySender replySender) {
        this.replySender = replySender;
        this.outboundDeliveryRouter = replySender instanceof OutboundDeliveryRouter r ? r : null;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        if (replySender == null) {
            return "Reply sender not available.";
        }
        String channelId = firstNonBlank(getString(bind, "channelId"), state != null ? getString(state, "channelId") : null);
        if (channelId == null || channelId.isBlank()) {
            return "Missing channelId for post_channel_message.";
        }
        String deliveryChannelId = firstNonBlank(getString(bind, "deliveryChannelId"), state != null ? getString(state, "deliveryChannelId") : null);
        String targetChannelId = firstNonBlank(getString(bind, "targetChannelId"), state != null ? getString(state, "targetChannelId") : null);
        String target = firstNonBlank(getString(bind, "target"), state != null ? getString(state, "target") : null);
        String sendTarget;
        if (targetChannelId != null && !targetChannelId.isBlank()) {
            sendTarget = targetChannelId;
        } else if ("room".equalsIgnoreCase(target != null ? target.trim() : null)) {
            sendTarget = channelId;
        } else if ("thread".equalsIgnoreCase(target != null ? target.trim() : null)) {
            sendTarget = firstNonBlank(deliveryChannelId, channelId);
            if (CreateThreadAction.THREAD_CREATE_FAILED.equals(sendTarget)) {
                sendTarget = channelId;
            }
        } else {
            sendTarget = firstNonBlank(deliveryChannelId, channelId);
            if (CreateThreadAction.THREAD_CREATE_FAILED.equals(sendTarget)) {
                sendTarget = channelId;
            }
        }
        String content = firstNonBlank(getString(bind, "content"), state != null ? getString(state, "content") : null);
        if (content == null) {
            content = "";
        }
        Map<String, Object> merged = new HashMap<>();
        if (state != null) merged.putAll(state);
        if (bind != null) merged.putAll(bind);
        content = interpolate(content, merged);
        if (content.isBlank()) {
            return "Blank content for post_channel_message.";
        }
        String asBotId = firstNonBlank(getString(bind, "asBotId"), state != null ? getString(state, "asBotId") : null);
        String asRoleStr = firstNonBlank(getString(bind, "asRole"), state != null ? getString(state, "asRole") : null);
        boolean explicitBot = asBotId != null && !asBotId.isBlank();
        boolean explicitRole = asRoleStr != null && !asRoleStr.isBlank();
        if (explicitBot || explicitRole) {
            if (outboundDeliveryRouter == null) {
                return "post_channel_message with asBotId/asRole requires OutboundDeliveryRouter as reply sender.";
            }
            if (explicitBot) {
                Optional<String> err = outboundDeliveryRouter.sendAsExplicit(sendTarget, null, content, asBotId);
                return err.orElse("OK");
            }
            PlanningRole role = parseRole(asRoleStr);
            if (role == null) {
                return "Invalid asRole for post_channel_message: " + asRoleStr;
            }
            Optional<String> err = outboundDeliveryRouter.sendAsRoleExplicit(sendTarget, null, content, role);
            return err.orElse("OK");
        }
        replySender.send(sendTarget, null, content);
        return "OK";
    }

    private static PlanningRole parseRole(String s) {
        if (s == null || s.isBlank()) return null;
        String upper = s.trim().toUpperCase();
        for (PlanningRole r : PlanningRole.values()) {
            if (r.name().equals(upper)) return r;
        }
        return null;
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }

    /** Replaces {{key}} in template with state.get(key) for all keys in state. */
    private static String interpolate(String template, Map<String, Object> state) {
        if (template == null || state == null || state.isEmpty()) return template != null ? template : "";
        String out = template;
        for (String key : state.keySet()) {
            if (key == null) continue;
            String placeholder = "{{" + key + "}}";
            if (out.contains(placeholder)) {
                Object v = state.get(key);
                out = out.replace(placeholder, v != null ? v.toString() : "");
            }
        }
        return out;
    }
}
