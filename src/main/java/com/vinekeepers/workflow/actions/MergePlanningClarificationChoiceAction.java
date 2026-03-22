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
        spread.put("planningClarificationMergeOk", "false");
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

            planStateStore.update(plan);

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

            appendCoordinatorClarificationToExploration(event, base, upsert, q, choice);
            spread.put("planningClarificationMerged", "true");
            spread.put("planningClarificationChoicesJson", "[]");
            spread.put("planningClarificationMetaJson", "{}");
            spread.put("planningUserInputRequired", "false");
            spread.put("planningPhase", "REVISING");
            spread.put("planningClarificationRaw", "");
            spread.put("planningClarificationMergeOk", "true");
            spread.put("planningClarificationRepeatCount", "0");
            String qForPrev =
                    !q.isBlank()
                            ? q
                            : firstNonBlank(
                                    getString(state, "planningClarificationQuestionText"), "");
            spread.put(
                    "planningPreviousClarificationQuestionText",
                    com.vinekeepers.workflow.planning.PlanningCyclePipeline.normalizeClarificationQuestion(qForPrev));
            return spread;
        } catch (Exception e) {
            spread.put("planningClarificationMergeError", e.getMessage() != null ? e.getMessage() : "merge failed");
            return spread;
        }
    }

    private static void appendCoordinatorClarificationToExploration(
            Event event,
            Map<String, Object> base,
            UpsertArtifactSectionDataAction upsert,
            String questionText,
            String userAnswer) {
        String q = questionText != null ? questionText.trim() : "";
        String a = userAnswer != null ? userAnswer.trim() : "";
        if (a.length() > 4000) {
            a = a.substring(0, 3999) + "…";
        }
        StringBuilder block = new StringBuilder();
        block.append("\n\n### Coordinator clarification (user)\n\n");
        if (!q.isBlank()) {
            block.append("**Question:** ").append(q).append("\n\n");
        }
        block.append("**Your answer:** ").append(a.isBlank() ? "(empty)" : a).append("\n");
        Object r =
                upsert.run(
                        event,
                        base,
                        Map.of(
                                "artifactId",
                                "request_exploration",
                                "sectionId",
                                "analysis",
                                "mode",
                                "append",
                                "data",
                                Map.of("exploration_body", block.toString())));
        if (!"OK".equals(String.valueOf(r))) {
            // Profile may omit request_exploration; drafting still has assumptions + decision_log.
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
