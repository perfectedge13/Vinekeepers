package com.vinekeepers.workflow.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.PlanConfidence;
import com.vinekeepers.state.planning.PlanIssue;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.workflow.deliberation.DeliberationDirtyPassIndex;
import com.vinekeepers.workflow.deliberation.DeliberationEngine;
import com.vinekeepers.workflow.planning.CanonicalMergeTargetPaths;
import com.vinekeepers.workflow.planning.PlanningDeliberationLedgerSync;
import com.vinekeepers.workflow.planning.PlanningGapAskCounts;
import com.vinekeepers.workflow.planning.PlanningRoutingBridge;
import com.vinekeepers.workflow.planreview.PlanningUserFacingCopy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Applies the user's clarification into the declared artifact slice only (via {@code gapId} + {@code mergeTargetPath}),
 * updates ledger merge state, records structured outcomes, and clears ephemeral clarification fields. The next planning
 * cycle must run silent synthesis before selecting another gap.
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
            String coordinatorGapId = text(meta, "gapId");
            if (coordinatorGapId.isBlank()) {
                spread.put(
                        "planningClarificationMergeError",
                        PlanningUserFacingCopy.humanizePlanningClarificationMergeError("MERGE_TARGET_MISSING"));
                return spread;
            }
            if (!CanonicalMergeTargetPaths.declaredMergeTargetMatchesGap(mergeTargetPath, coordinatorGapId)) {
                spread.put(
                        "planningClarificationMergeError",
                        PlanningUserFacingCopy.humanizePlanningClarificationMergeError("MERGE_TARGET_MISMATCH"));
                return spread;
            }

            String defaultText = text(meta, "defaultAssumption");
            String optA = text(meta, "optA");
            String optB = text(meta, "optB");
            String q = text(meta, "questionText");
            String choicesRaw = firstNonBlank(getString(state, "planningClarificationChoicesJson"), "[]");
            boolean structuredUi = choicesRaw != null && !choicesRaw.isBlank() && !"[]".equals(choicesRaw.trim());
            String trimmedChoice = choice.trim();

            MergeInterpretation interpretation =
                    structuredUi
                                    && ("planning_clarify_default".equals(trimmedChoice)
                                            || "planning_clarify_opt_a".equals(trimmedChoice)
                                            || "planning_clarify_opt_b".equals(trimmedChoice)
                                            || "planning_clarify_opt_c".equals(trimmedChoice))
                            ? MergeInterpretation.RESOLVED
                            : interpretOpenText(trimmedChoice);

            String answerSummary;
            String decisionLine;
            if ("planning_clarify_default".equals(trimmedChoice)) {
                answerSummary = defaultText.isBlank() ? "Use recommended baseline." : defaultText;
                decisionLine = "Decision (user selected default): " + answerSummary;
            } else if ("planning_clarify_opt_a".equals(trimmedChoice)) {
                answerSummary = optA.isBlank() ? "Option A" : optA;
                decisionLine = "Decision (user choice A): " + answerSummary;
            } else if ("planning_clarify_opt_b".equals(trimmedChoice)) {
                answerSummary = optB.isBlank() ? "Option B" : optB;
                decisionLine = "Decision (user choice B): " + answerSummary;
            } else if ("planning_clarify_opt_c".equals(trimmedChoice)) {
                String optC = text(meta, "optC");
                answerSummary = optC.isBlank() ? "Option C" : optC;
                decisionLine = "Decision (user choice C): " + answerSummary;
            } else {
                answerSummary = trimmedChoice;
                if (interpretation == MergeInterpretation.PARTIAL) {
                    String frag =
                            trimmedChoice.toLowerCase(Locale.ROOT).startsWith("partial:")
                                    ? trimmedChoice.substring("partial:".length()).trim()
                                    : trimmedChoice;
                    answerSummary = frag;
                    decisionLine = "Partial (confirmed fragment) for gap `" + coordinatorGapId + "`: " + frag;
                } else if (interpretation == MergeInterpretation.CONTRADICTION) {
                    decisionLine =
                            "User contradiction noted for gap `"
                                    + coordinatorGapId
                                    + "` (target slice only): "
                                    + trimmedChoice;
                } else {
                    decisionLine = "Decision (user selection): " + trimmedChoice + (q.isBlank() ? "" : " regarding: " + q);
                }
            }
            String explicitResolutionLine =
                    coordinatorGapId == null || coordinatorGapId.isBlank() || answerSummary.isBlank()
                            ? ""
                            : "Coordinator gap resolution (" + coordinatorGapId + "): " + answerSummary;
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
                                    "replace",
                                    "data",
                                    Map.of(fieldKey, decisionLine)));
            if (!"OK".equals(String.valueOf(mergeUpsert))) {
                spread.put(
                        "planningClarificationMergeError",
                        PlanningUserFacingCopy.humanizePlanningClarificationMergeError("MERGE_TARGET_INVALID"));
                return spread;
            }

            FeaturePlanState merged = planStateStore.getByContextId(contextId).orElse(plan);
            String outcomeTag =
                    interpretation == MergeInterpretation.PARTIAL
                            ? "partial"
                            : (interpretation == MergeInterpretation.CONTRADICTION ? "contradiction" : "resolved");
            FeaturePlanState afterNote =
                    merged.withClarificationEngineNote(mergeOutcomeNote(outcomeTag, coordinatorGapId, mergeTargetPath));
            if (interpretation == MergeInterpretation.CONTRADICTION) {
                afterNote =
                        afterNote.withAppendedIssue(
                                        PlanIssue.fromLegacyText(
                                                "iss-clar-contra-"
                                                        + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                                                "Clarification reply may contradict inferred plan content for gap `"
                                                        + coordinatorGapId
                                                        + "`.",
                                                Instant.now()))
                                .withPlanConfidence(
                                        new PlanConfidence(
                                                "LOW",
                                                "Clarification contradiction recorded for gap " + coordinatorGapId,
                                                null,
                                                Instant.now(),
                                                0.12,
                                                List.of("CLARIFICATION_CONTRADICTION")));
            }
            planStateStore.update(afterNote);

            spread.put("planningClarificationMerged", "true");
            spread.put("planningJustMergedClarification", "true");
            spread.put("planningClarificationChoicesJson", "[]");
            spread.put("planningClarificationMetaJson", "{}");
            spread.put("planningClarificationQuestionText", "");
            spread.put("planningCanonicalUserInputRequired", "false");
            spread.put("planningCanonicalNextAction", "BLOCK");
            spread.put("planningNextAction", "BLOCKED");
            spread.put(PlanningRoutingBridge.NEXT_PHASE_KEY, PlanningRoutingBridge.PHASE_PLANNING_BLOCKED);
            spread.put("planningNextQuestion", "");
            spread.put("planningBlockingReason", "");
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
            spread.put("planningPreviousClarificationQuestionText", qForPrev != null ? qForPrev.trim() : "");
            if (interpretation == MergeInterpretation.PARTIAL) {
                spread.put(
                        "planningClarificationMergeError",
                        PlanningUserFacingCopy.humanizePlanningClarificationMergeError("PARTIAL_MERGE"));
            } else if (interpretation == MergeInterpretation.CONTRADICTION) {
                spread.put(
                        "planningClarificationMergeError",
                        PlanningUserFacingCopy.humanizePlanningClarificationMergeError("CONTRADICTION_RECORDED"));
            }
            return spread;
        } catch (Exception e) {
            spread.put(
                    "planningClarificationMergeError",
                    PlanningUserFacingCopy.humanizePlanningClarificationMergeError(
                            e.getMessage() != null ? e.getMessage() : "merge failed"));
            return spread;
        }
    }

    private static String mergeOutcomeNote(String kind, String gapId, String mergeTargetPath) {
        return "merge:outcome:"
                + kind
                + ":gapId="
                + gapId
                + ":path="
                + mergeTargetPath.replace('\n', ' ')
                + ":"
                + Instant.now();
    }

    private enum MergeInterpretation {
        RESOLVED,
        PARTIAL,
        CONTRADICTION
    }

    private static MergeInterpretation interpretOpenText(String raw) {
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if (t.matches("^(yes|no|y|n|ok|okay)$")) {
            return MergeInterpretation.RESOLVED;
        }
        if (t.startsWith("partial:")) {
            return MergeInterpretation.PARTIAL;
        }
        if (t.contains("wrong")
                || t.contains("incorrect")
                || t.contains("disagree")
                || t.contains("contradict")
                || t.contains("not accurate")) {
            return MergeInterpretation.CONTRADICTION;
        }
        return MergeInterpretation.RESOLVED;
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
