package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Posts the planning-cycle progress line only when {@code planningProgressPostWorthy} is true (content fingerprint
 * differs from {@code planningLastProgressPostHash}). Otherwise spreads {@code planningProgressPostSkipped} true
 * without sending. On successful send, updates {@code planningLastProgressPostHash} from
 * {@code planningProgressPostFingerprint}.
 */
public final class PostPlanningProgressIfChangedAction implements com.vinekeepers.workflow.WorkflowAction {

    private final PostChannelMessageAction postChannelMessageAction;

    public PostPlanningProgressIfChangedAction(PostChannelMessageAction postChannelMessageAction) {
        this.postChannelMessageAction = postChannelMessageAction;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        Map<String, Object> merged = new HashMap<>();
        if (state != null) {
            merged.putAll(state);
        }
        if (bind != null) {
            merged.putAll(bind);
        }
        if (!truthy(getString(merged, "planningProgressPostWorthy"))) {
            spread.put("planningProgressPostSkipped", "true");
            return spread;
        }
        Object sendResult = postChannelMessageAction.run(event, state, bind);
        spread.put("planningProgressPostSkipped", "false");
        spread.put("planningProgressPostSendResult", String.valueOf(sendResult));
        if ("OK".equals(String.valueOf(sendResult))) {
            String fp = getString(merged, "planningProgressPostFingerprint");
            if (fp != null && !fp.isBlank()) {
                spread.put("planningLastProgressPostHash", fp);
            }
        }
        return spread;
    }

    private static boolean truthy(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim().toLowerCase();
        return "true".equals(t) || "1".equals(t) || "yes".equals(t);
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
