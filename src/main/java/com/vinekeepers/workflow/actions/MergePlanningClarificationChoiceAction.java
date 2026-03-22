package com.vinekeepers.workflow.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.AssumptionEntry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Applies the user's clarification choice (from {@code planningClarificationChoiceProvider}) into assumptions
 * and decision log, then clears ephemeral clarification JSON for the next cycle.
 */
public final class MergePlanningClarificationChoiceAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final FeaturePlanStateStore planStateStore;
    private final WorkProfileRegistry workProfileRegistry;

    public MergePlanningClarificationChoiceAction(
            FeaturePlanStateStore planStateStore, WorkProfileRegistry workProfileRegistry) {
        this.planStateStore = planStateStore;
        this.workProfileRegistry = workProfileRegistry;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("planningClarificationMerged", "false");
        spread.put("planningClarificationMergeError", "");
        if (planStateStore == null || workProfileRegistry == null) {
            spread.put("planningClarificationMergeError", "MISSING_DEPS");
            return spread;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            spread.put("planningClarificationMergeError", "NO_CONTEXT");
            return spread;
        }
        String choice = firstNonBlank(getString(bind, "planningClarificationRaw"), getString(state, "planningClarificationRaw"));
        if (choice == null || choice.isBlank()) {
            spread.put("planningClarificationMergeError", "NO_CHOICE");
            return spread;
        }
        String metaRaw = firstNonBlank(getString(state, "planningClarificationMetaJson"), "{}");
        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            spread.put("planningClarificationMergeError", "NO_PLAN");
            return spread;
        }
        try {
            JsonNode meta = JSON.readTree(metaRaw);
            String defaultText = text(meta, "defaultAssumption");
            String optA = text(meta, "optA");
            String optB = text(meta, "optB");
            String q = text(meta, "questionText");
            String decisionLine;
            if ("planning_clarify_default".equals(choice)) {
                decisionLine = "Decision (user selected default): " + (defaultText.isBlank() ? "Use recommended baseline." : defaultText);
                if (!defaultText.isBlank()) {
                    plan = plan.withAppendedAssumption(new AssumptionEntry(
                            "asm-clar-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                            defaultText,
                            null));
                }
            } else if ("planning_clarify_opt_a".equals(choice)) {
                decisionLine = "Decision (user choice A): " + (optA.isBlank() ? "Option A" : optA);
                plan = plan.withAppendedAssumption(new AssumptionEntry(
                        "asm-clar-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                        decisionLine,
                        null));
            } else if ("planning_clarify_opt_b".equals(choice)) {
                decisionLine = "Decision (user choice B): " + (optB.isBlank() ? "Option B" : optB);
                plan = plan.withAppendedAssumption(new AssumptionEntry(
                        "asm-clar-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                        decisionLine,
                        null));
            } else if ("planning_clarify_opt_c".equals(choice)) {
                String optC = text(meta, "optC");
                decisionLine = "Decision (user choice C): " + (optC.isBlank() ? "Option C" : optC);
                plan = plan.withAppendedAssumption(new AssumptionEntry(
                        "asm-clar-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                        decisionLine,
                        null));
            } else {
                decisionLine = "Decision (user selection): " + choice + (q.isBlank() ? "" : " regarding: " + q);
                plan = plan.withAppendedAssumption(new AssumptionEntry(
                        "asm-clar-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                        decisionLine,
                        null));
            }

            UpsertArtifactSectionDataAction upsert = new UpsertArtifactSectionDataAction(planStateStore, workProfileRegistry);
            Map<String, Object> base = new LinkedHashMap<>();
            if (state != null) {
                base.putAll(state);
            }
            base.put("contextId", contextId);
            upsert.run(
                    event,
                    base,
                    Map.of(
                            "artifactId",
                            "decision_log",
                            "sectionId",
                            "decisions",
                            "mode",
                            "append",
                            "data",
                            Map.of("decision_text", decisionLine)));

            planStateStore.update(plan);
            spread.put("planningClarificationMerged", "true");
            spread.put("planningClarificationChoicesJson", "[]");
            spread.put("planningClarificationMetaJson", "{}");
            spread.put("planningUserInputRequired", "false");
            spread.put("planningPhase", "REVISING");
            return spread;
        } catch (Exception e) {
            spread.put("planningClarificationMergeError", e.getMessage() != null ? e.getMessage() : "merge failed");
            return spread;
        }
    }

    private static String text(JsonNode meta, String field) {
        if (meta == null) {
            return "";
        }
        JsonNode n = meta.get(field);
        return n != null && n.isTextual() ? n.asText("") : "";
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
