package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.workflow.discovery.StructuredDiscoverySupport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the next discovery question fields from {@code discoveryGapsJson} in workflow state.
 */
public final class BuildDiscoveryAgendaAction implements com.vinekeepers.workflow.WorkflowAction {

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> err = new LinkedHashMap<>();
        String gapsJson = firstNonBlank(getString(bind, "discoveryGapsJson"), getString(state, "discoveryGapsJson"));
        if (gapsJson == null || gapsJson.isBlank()) {
            err.put("discoveryCurrentQuestionPrompt", "");
            err.put("discoveryCurrentQuestionId", "");
            err.put("discoveryApplyKind", "NONE");
            err.put("discoveryApplyArtifactId", "");
            err.put("discoveryApplySectionId", "");
            err.put("discoveryApplyFieldId", "");
            err.put("discoveryApplyMode", "replace");
            err.put("discoveryAgendaJson", "{}");
            err.put("discoveryAgendaError", "Missing discoveryGapsJson.");
            return err;
        }
        try {
            return StructuredDiscoverySupport.buildAgendaSpread(gapsJson);
        } catch (Exception e) {
            err.put("discoveryCurrentQuestionPrompt", "");
            err.put("discoveryCurrentQuestionId", "");
            err.put("discoveryApplyKind", "NONE");
            err.put("discoveryApplyArtifactId", "");
            err.put("discoveryApplySectionId", "");
            err.put("discoveryApplyFieldId", "");
            err.put("discoveryApplyMode", "replace");
            err.put("discoveryAgendaJson", "{}");
            err.put("discoveryAgendaError", e.getMessage() != null ? e.getMessage() : "agenda build failed");
            return err;
        }
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
