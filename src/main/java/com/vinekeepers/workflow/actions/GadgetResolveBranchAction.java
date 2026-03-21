package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Workflow action: set {@code deployBranch} from {@code branchChoice} (main/develop/…) or {@code branchCustom}
 * when choice is {@code other}.
 */
public final class GadgetResolveBranchAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final String OTHER = "other";

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> args = mergedArgs(state, bind);
        String choice = getString(args, "branchChoice");
        String deployBranch;
        if (choice != null && OTHER.equalsIgnoreCase(choice.trim())) {
            deployBranch = firstNonBlank(getString(args, "branchCustom"), "main");
        } else {
            deployBranch = firstNonBlank(choice, "main");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deployBranch", deployBranch);
        return out;
    }

    private static Map<String, Object> mergedArgs(Map<String, Object> state, Map<String, Object> bind) {
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
        return b != null ? b : "";
    }
}
