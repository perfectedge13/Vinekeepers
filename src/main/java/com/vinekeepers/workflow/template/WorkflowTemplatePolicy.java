package com.vinekeepers.workflow.template;

import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.viewmodel.UserCopyContext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Workflow-level policy for interpolating {@code {{key}}} in user-facing templates.
 * When {@code exposeInternal} is false, only coordinator view-model keys (or {@code allowedKeys}) are substituted.
 */
public final class WorkflowTemplatePolicy {

    /** Legacy: substitute any key present in state (full map scan). */
    public static final WorkflowTemplatePolicy LEGACY_FULL_STATE = new WorkflowTemplatePolicy(true, List.of());

    public static final String STATE_EXPOSE_INTERNAL = "__workflowTemplatesExposeInternal";
    public static final String STATE_ALLOWED_KEYS = "__workflowTemplatesAllowedKeys";

    private final boolean exposeInternal;
    private final List<String> allowedKeys;

    private WorkflowTemplatePolicy(boolean exposeInternal, List<String> allowedKeys) {
        this.exposeInternal = exposeInternal;
        this.allowedKeys = allowedKeys != null ? List.copyOf(allowedKeys) : List.of();
    }

    /**
     * Parses {@code templates} from workflow YAML: {@code exposeInternal} (default true if absent),
     * optional {@code allowedKeys} string list (when non-empty, replaces default coordinator keys for safe mode).
     */
    public static WorkflowTemplatePolicy fromYaml(Object yaml) {
        if (!(yaml instanceof Map<?, ?> m)) {
            return LEGACY_FULL_STATE;
        }
        Object ei = m.get("exposeInternal");
        boolean expose = true;
        if (ei != null) {
            expose = parseTruthy(ei);
        }
        List<String> keys = parseStringList(m.get("allowedKeys"));
        return new WorkflowTemplatePolicy(expose, keys);
    }

    public boolean isExposeInternal() {
        return exposeInternal;
    }

    public List<String> getAllowedKeys() {
        return allowedKeys;
    }

    public Set<String> effectiveAllowedKeys() {
        if (!allowedKeys.isEmpty()) {
            return new LinkedHashSet<>(allowedKeys);
        }
        return UserCopyContext.DEFAULT_COORDINATOR_KEYS;
    }

    public void writeIntoState(ConfigurableWorkflowState state) {
        if (state == null) {
            return;
        }
        state.put(STATE_EXPOSE_INTERNAL, Boolean.valueOf(exposeInternal));
        if (allowedKeys.isEmpty()) {
            state.clearKeys(List.of(STATE_ALLOWED_KEYS));
        } else {
            state.put(STATE_ALLOWED_KEYS, new ArrayList<>(allowedKeys));
        }
    }

    /**
     * Reads policy previously written by {@link #writeIntoState}; absent keys imply {@link #LEGACY_FULL_STATE}.
     */
    public static WorkflowTemplatePolicy readFromStateMap(Map<String, ?> data) {
        if (data == null) {
            return LEGACY_FULL_STATE;
        }
        Object rawExpose = data.get(STATE_EXPOSE_INTERNAL);
        if (rawExpose == null) {
            return LEGACY_FULL_STATE;
        }
        boolean expose = rawExpose instanceof Boolean b ? b : Boolean.parseBoolean(rawExpose.toString());
        List<String> keys = parseStringList(data.get(STATE_ALLOWED_KEYS));
        return new WorkflowTemplatePolicy(expose, keys);
    }

    private static boolean parseTruthy(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        String s = o.toString().trim().toLowerCase();
        return !("false".equals(s) || "0".equals(s) || "no".equals(s));
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseStringList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null && !o.toString().isBlank()) {
                    out.add(o.toString().trim());
                }
            }
            return out;
        }
        return List.of();
    }
}
