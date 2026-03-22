package com.vinekeepers.workflow.deliberation;

import com.vinekeepers.workflow.planning.ConfigurablePassRunner;
import com.vinekeepers.workflow.planning.PlanningCoordinatorRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Conservative invalidation: artifact path changes rerun all configured coordinator passes unless mapped per-path (future).
 */
public final class DeliberationDirtyPassIndex {

    private DeliberationDirtyPassIndex() {}

    /**
     * @param affectedPaths artifact paths or section keys (may be empty)
     * @return stable pass ids (role names) to rerun after merge
     */
    public static List<String> closureForArtifactChanges(
            Map<String, Object> state, Map<String, Object> bind, List<String> affectedPaths) {
        List<PlanningCoordinatorRole> order = ConfigurablePassRunner.resolveOrder(state, bind);
        List<String> out = new ArrayList<>();
        for (PlanningCoordinatorRole r : order) {
            out.add(r.name());
        }
        if (out.isEmpty()) {
            return List.of();
        }
        if (affectedPaths == null || affectedPaths.isEmpty()) {
            return List.copyOf(out);
        }
        // Until per-path → pass mapping exists in config, any change conservatively reruns the full pass list.
        return List.copyOf(out);
    }

    /** JSON-friendly map for workflow spread ({@code planningDirtyPassesJson}). */
    public static String closureJson(Map<String, Object> state, Map<String, Object> bind, List<String> affectedPaths) {
        List<String> ids = closureForArtifactChanges(state, bind, affectedPaths);
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(ids.get(i).toUpperCase(Locale.ROOT)).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    public static void writeDirtyPassesSpread(
            Map<String, Object> spread, Map<String, Object> state, Map<String, Object> bind, List<String> affectedPaths) {
        if (spread == null) {
            return;
        }
        spread.put("planningDirtyPassesJson", closureJson(state, bind, affectedPaths));
        spread.put("planningDirtyPassCount", String.valueOf(closureForArtifactChanges(state, bind, affectedPaths).size()));
    }
}
