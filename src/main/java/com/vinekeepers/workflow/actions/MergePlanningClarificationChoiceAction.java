package com.vinekeepers.workflow.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.CoordinatorClarificationGapRule;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.PlanIssue;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.workflow.deliberation.DeliberationDirtyPassIndex;
import com.vinekeepers.workflow.deliberation.DeliberationEngine;
import com.vinekeepers.workflow.planning.CoordinatorClarificationGapEvaluator;
import com.vinekeepers.workflow.planning.PlanningDeliberationLedgerSync;
import com.vinekeepers.workflow.planning.PlanningGapAskCounts;
import com.vinekeepers.workflow.planreview.PlanningUserFacingCopy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies the user's clarification choice into the declared artifact slice only, updates ledger merge state, and clears
 * ephemeral clarification fields. The next planning cycle runs synthesis and canonical gap derivation (no post-merge gap
 * evaluation here).
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
            spread.put(
                    "planningClarificationMergeError",
                    PlanningUserFacingCopy.humanizePlanningClarificationMergeError("MISSING_DEPS"));
            return spread;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            spread.put(
                    "planningClarificationMergeError",
                    PlanningUserFacingCopy.humanizePlanningClarificationMergeError("NO_CONTEXT"));
            return spread;
        }
        String choice = firstNonBlank(getString(bind, "planningClarificationRaw"), getString(state, "planningClarificationRaw"));
        if (choice == null || choice.isBlank()) {
            spread.put(
                    "planningClarificationMergeError",
                    PlanningUserFacingCopy.humanizePlanningClarificationMergeError("NO_CHOICE"));
            return spread;
        }
        String metaRaw = firstNonBlank(getString(state, "planningClarificationMetaJson"), "{}");
        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            spread.put(
                    "planningClarificationMergeError",
                    PlanningUserFacingCopy.humanizePlanningClarificationMergeError("NO_PLAN"));
            return spread;
        }
        try {
            JsonNode meta = JSON.readTree(metaRaw);
            String mergeTargetPath = text(meta, "mergeTargetPath");
            if (mergeTargetPath.isBlank()) {
                spread.put(
                        "planningClarificationMergeError",
                        PlanningUserFacingCopy.humanizePlanningClarificationMergeError("MERGE_TARGET_MISSING"));
                return spread;
            }
            String[] mergeParts = mergeTargetPath.split("/");
            if (mergeParts.length != 3
                    || mergeParts[0].isBlank()
                    || mergeParts[1].isBlank()
                    || mergeParts[2].isBlank()) {
                spread.put(
                        "planningClarificationMergeError",
                        PlanningUserFacingCopy.humanizePlanningClarificationMergeError("MERGE_TARGET_INVALID"));
                return spread;
            }
            String defaultText = text(meta, "defaultAssumption");
            String optA = text(meta, "optA");
            String optB = text(meta, "optB");
            String q = text(meta, "questionText");
            String coordinatorGapId = text(meta, "gapId");
            String choicesRaw = firstNonBlank(getString(state, "planningClarificationChoicesJson"), "[]");
            boolean structuredUi = choicesRaw != null && !choicesRaw.isBlank() && !"[]".equals(choicesRaw.trim());
            String trimmedChoice = choice.trim();
            if (structuredUi
                    && !"planning_clarify_default".equals(trimmedChoice)
                    && !"planning_clarify_opt_a".equals(trimmedChoice)
                    && !"planning_clarify_opt_b".equals(trimmedChoice)
                    && !"planning_clarify_opt_c".equals(trimmedChoice)) {
                plan =
                        plan.withAppendedIssue(
                                        PlanIssue.fromLegacyText(
                                                "iss-clar-amb-"
                                                        + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                                                "Ambiguous clarification reply for gap `"
                                                        + coordinatorGapId
                                                        + "` (expected a structured choice id).",
                                                Instant.now()))
                                .withPlanningGapAskCountsJson(
                                        PlanningGapAskCounts.incrementFailedMerge(
                                                plan.getPlanningGapAskCountsJson(), coordinatorGapId))
                                .withClarificationEngineNote(
                                        "ambiguous:" + coordinatorGapId + ":" + Instant.now());
                planStateStore.update(plan);
                spread.put(
                        "planningClarificationMergeError",
                        PlanningUserFacingCopy.humanizePlanningClarificationMergeError("AMBIGUOUS_REPLY"));
                return spread;
            }
            WorkProfileDefinition profileDef =
                    plan.getProfileId() != null && !plan.getProfileId().isBlank()
                            ? workProfileRegistry.get(plan.getProfileId()).orElse(null)
                            : null;
            CoordinatorClarificationGapRule coordinatorGapRule =
                    profileDef != null && profileDef.getCoordinatorClarification().isCanonicalV1()
                            ? profileDef.getCoordinatorClarification().findGapRule(coordinatorGapId).orElse(null)
                            : null;
            String decisionLine;
            String answerSummary;
            if ("planning_clarify_default".equals(choice)) {
                answerSummary = defaultText.isBlank() ? "Use recommended baseline." : defaultText;
                decisionLine = "Decision (user selected default): " + answerSummary;
            } else if ("planning_clarify_opt_a".equals(choice)) {
                answerSummary = optA.isBlank() ? "Option A" : optA;
                decisionLine = "Decision (user choice A): " + answerSummary;
            } else if ("planning_clarify_opt_b".equals(choice)) {
                answerSummary = optB.isBlank() ? "Option B" : optB;
                decisionLine = "Decision (user choice B): " + answerSummary;
            } else if ("planning_clarify_opt_c".equals(choice)) {
                String optC = text(meta, "optC");
                answerSummary = optC.isBlank() ? "Option C" : optC;
                decisionLine = "Decision (user choice C): " + answerSummary;
            } else {
                answerSummary = choice.trim();
                decisionLine = "Decision (user selection): " + choice + (q.isBlank() ? "" : " regarding: " + q);
            }
            String explicitResolutionLine =
                    CoordinatorClarificationGapEvaluator.buildExplicitResolutionLine(
                            coordinatorGapId, coordinatorGapRule, answerSummary);
            if (!explicitResolutionLine.isBlank()) {
                decisionLine = decisionLine + "\n" + explicitResolutionLine;
            }

            UnresolvedItemLedger ledger = UnresolvedItemLedger.readFrom(state);
            String ledgerItemId = firstNonBlank(getString(state, "planningClarificationLedgerItemId"), "");
            String qText = q.isBlank() ? firstNonBlank(getString(state, "planningClarificationQuestionText"), "") : q;
            ledger =
                    PlanningDeliberationLedgerSync.mergeAnswerIntoLedger(
                            ledger, ledgerItemId, qText, coordinatorGapId, answerSummary, answerSummary.trim());
            UnresolvedItemLedger.mergeLedgerIntoSpread(spread, ledger);

            UpsertArtifactSectionDataAction upsert = new UpsertArtifactSectionDataAction(planStateStore, workProfileRegistry);
            Map<String, Object> base = new LinkedHashMap<>();
            if (state != null) {
                base.putAll(state);
            }
            base.put("contextId", contextId);
            String fieldKey = mergeParts[2].trim();
            Object mergeUpsert =
                    upsert.run(
                            event,
                            base,
                            Map.of(
                                    "artifactId",
                                    mergeParts[0].trim(),
                                    "sectionId",
                                    mergeParts[1].trim(),
                                    "mode",
                                    "append",
                                    "data",
                                    Map.of(fieldKey, decisionLine)));
            if (!"OK".equals(String.valueOf(mergeUpsert))) {
                spread.put(
                        "planningClarificationMergeError",
                        PlanningUserFacingCopy.humanizePlanningClarificationMergeError("MERGE_TARGET_INVALID"));
                return spread;
            }

            FeaturePlanState merged = planStateStore.getByContextId(contextId).orElse(plan);
            String outcomeLine =
                    "merge:outcome:resolved:gapId="
                            + coordinatorGapId
                            + ":path="
                            + mergeTargetPath.replace('\n', ' ')
                            + ":"
                            + Instant.now();
            planStateStore.update(merged.withClarificationEngineNote(outcomeLine));

            spread.put("planningClarificationMerged", "true");
            spread.put("planningJustMergedClarification", "true");
            spread.put("planningClarificationChoicesJson", "[]");
            spread.put("planningClarificationMetaJson", "{}");
            spread.put("planningUserInputRequired", "false");
            spread.put("planningCanonicalUserInputRequired", "false");
            spread.put("planningPhase", "REVISING");
            DeliberationEngine.applyDerivedDeliberationSpread(spread);
            DeliberationDirtyPassIndex.writeDirtyPassesSpread(spread, state, bind, List.of("clarification_merge"));
            spread.put("planningClarificationRaw", "");
            spread.put("planningClarificationMergeOk", "true");
            spread.put("planningClarificationRepeatCount", "0");
            spread.put("planningClarificationLedgerItemId", "");
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
            spread.put(
                    "planningClarificationMergeError",
                    PlanningUserFacingCopy.humanizePlanningClarificationMergeError(
                            e.getMessage() != null ? e.getMessage() : "merge failed"));
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
