package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.discovery.StructuredDiscoverySupport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the next discovery clarification for intake planning: one {@code BLOCKER}/{@code HIGH} gap, canonical
 * user-facing text only (no bundled menus, no label-only templates).
 */
public final class BuildInsightDiscoveryAgendaAction implements com.vinekeepers.workflow.WorkflowAction {

    public BuildInsightDiscoveryAgendaAction(
            @SuppressWarnings("unused") FeaturePlanStateStore planStateStore,
            @SuppressWarnings("unused") WorkProfileRegistry workProfileRegistry) {}

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> err = emptySpread("Missing discoveryGapsJson.");
        String gapsJson = firstNonBlank(getString(bind, "discoveryGapsJson"), getString(state, "discoveryGapsJson"));
        if (gapsJson == null || gapsJson.isBlank()) {
            err.put("discoveryAgendaError", "Missing discoveryGapsJson.");
            return err;
        }
        try {
            return StructuredDiscoverySupport.buildIntakeBlockingClarificationSpread(gapsJson);
        } catch (Exception e) {
            err.put("discoveryAgendaError", e.getMessage() != null ? e.getMessage() : "insight agenda failed");
            return err;
        }
    }

    private static Map<String, Object> emptySpread(String errMsg) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("discoveryCurrentQuestionPrompt", "");
        err.put("discoveryCurrentQuestionId", "");
        err.put("discoveryApplyKind", "NONE");
        err.put("discoveryApplyArtifactId", "");
        err.put("discoveryApplySectionId", "");
        err.put("discoveryApplyFieldId", "");
        err.put("discoveryApplyMode", "replace");
        err.put("discoveryBundledApplyJson", "[]");
        err.put("discoveryAgendaJson", "{}");
        err.put("discoveryAgendaError", errMsg);
        return err;
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }
}
