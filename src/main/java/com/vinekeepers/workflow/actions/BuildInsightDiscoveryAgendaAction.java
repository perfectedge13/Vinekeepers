package com.vinekeepers.workflow.actions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.discovery.InsightDiscoverySupport;
import com.vinekeepers.workflow.discovery.StructuredDiscoverySupport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds discovery agenda; for v2+ profiles with multiple required-field gaps, uses one bundled insight prompt.
 */
public final class BuildInsightDiscoveryAgendaAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final FeaturePlanStateStore planStateStore;
    private final WorkProfileRegistry workProfileRegistry;

    public BuildInsightDiscoveryAgendaAction(
            FeaturePlanStateStore planStateStore,
            WorkProfileRegistry workProfileRegistry) {
        this.planStateStore = planStateStore;
        this.workProfileRegistry = workProfileRegistry;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> err = emptySpread("Missing discoveryGapsJson.");
        String gapsJson = firstNonBlank(getString(bind, "discoveryGapsJson"), getString(state, "discoveryGapsJson"));
        if (gapsJson == null || gapsJson.isBlank()) {
            err.put("discoveryAgendaError", "Missing discoveryGapsJson.");
            return err;
        }
        try {
            WorkProfileDefinition profile = null;
            String initialRequest = "";
            if (planStateStore != null && workProfileRegistry != null) {
                String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
                if (contextId != null && !contextId.isBlank()) {
                    FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
                    if (plan != null && plan.getProfileId() != null && !plan.getProfileId().isBlank()) {
                        profile = workProfileRegistry.get(plan.getProfileId()).orElse(null);
                        initialRequest = plan.getInitialRequest() != null ? plan.getInitialRequest() : "";
                    }
                }
            }
            initialRequest = mergeLlmFollowUps(initialRequest, state, bind);
            if (profile != null && profile.findSection("architecture_notes", "impact").isPresent()) {
                return InsightDiscoverySupport.buildInsightAgendaSpread(gapsJson, profile, initialRequest);
            }
            return StructuredDiscoverySupport.buildAgendaSpread(gapsJson);
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

    private static String mergeLlmFollowUps(String initialRequest, Map<String, Object> state, Map<String, Object> bind) {
        String raw = firstNonBlank(getString(bind, "planningFollowUpQuestionsJson"), getString(state, "planningFollowUpQuestionsJson"));
        if (raw == null || raw.isBlank() || "[]".equals(raw.trim())) {
            return initialRequest;
        }
        try {
            List<String> qs = JSON.readValue(raw, new TypeReference<>() {});
            if (qs == null || qs.isEmpty()) {
                return initialRequest;
            }
            String base = initialRequest != null ? initialRequest : "";
            StringBuilder b = new StringBuilder(base);
            b.append("\n\n---\n**Planning follow-up questions (synthesis):**\n");
            for (int i = 0; i < qs.size(); i++) {
                String q = qs.get(i);
                if (q != null && !q.isBlank()) {
                    b.append(i + 1).append(". ").append(q.trim()).append("\n");
                }
            }
            return b.toString();
        } catch (Exception e) {
            return initialRequest;
        }
    }
}
