package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.DiscordReplySender;
import com.vinekeepers.events.Event;

import java.util.HashMap;
import java.util.Map;

/**
 * Workflow action: state-driven post to a channel (e.g. lifecycle room). Bind/state: channelId, content.
 * Interpolation uses a merged map (state then bind); bind overrides state, so {{lifecycleBotName}} from bind wins.
 */
public final class PostChannelMessageAction implements com.vinekeepers.workflow.WorkflowAction {

    private final DiscordReplySender replySender;

    public PostChannelMessageAction(DiscordReplySender replySender) {
        this.replySender = replySender;
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
        replySender.send(channelId, null, content);
        return "OK";
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
