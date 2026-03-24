package com.vinekeepers.workflow.actions;

import com.vinekeepers.profile.ArtifactDefinition;
import com.vinekeepers.profile.ArtifactState;
import com.vinekeepers.profile.CoordinatorClarificationGapRule;
import com.vinekeepers.profile.CoordinatorClarificationMode;
import com.vinekeepers.profile.CoordinatorClarificationSettings;
import com.vinekeepers.profile.FieldDefinition;
import com.vinekeepers.profile.SectionDefinition;
import com.vinekeepers.profile.SectionState;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.planning.CanonicalMergeTargetPaths;
import com.vinekeepers.workflow.planreview.PlanningArtifactTexts;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MergePlanningClarificationChoiceActionTest {

    @Test
    @SuppressWarnings("unchecked")
    void canonicalV1MergeSetsUserInputFalseWhenNoGapsConfigured() {
        WorkProfileRegistry reg = new WorkProfileRegistry();
        reg.register(
                new WorkProfileDefinition(
                        "p_merge_canon",
                        "",
                        minimalArtifactsForDecisionLogMerge(),
                        List.of(),
                        false,
                        false,
                        List.of(),
                        new CoordinatorClarificationSettings(CoordinatorClarificationMode.CANONICAL_V1, List.of())));
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        store.update(plan("ctx-merge-a", "p_merge_canon", "hello"));
        var action = new MergePlanningClarificationChoiceAction(store, reg);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("contextId", "ctx-merge-a");
        state.put("planningClarificationRaw", "config only");
        state.put(
                "planningClarificationMetaJson",
                metaJson("g1", "Q?", CanonicalMergeTargetPaths.defaultMergeTargetPath("g1")));
        Map<String, Object> spread = (Map<String, Object>) action.run(null, state, Map.of("contextId", "ctx-merge-a"));
        assertEquals("true", spread.get("planningClarificationMergeOk"));
        assertEquals("false", spread.get("planningCanonicalUserInputRequired"));
        assertFalse(spread.containsKey("planningUserInputRequired"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void canonicalV1MergeClearsUserInputFlagsForNextPlanningCycle() {
        CoordinatorClarificationGapRule alwaysOpen =
                new CoordinatorClarificationGapRule(
                        "g_sticky",
                        false,
                        "More detail?",
                        List.of(),
                        List.of("the"),
                        List.of());
        WorkProfileRegistry reg = new WorkProfileRegistry();
        reg.register(
                new WorkProfileDefinition(
                        "p_merge_sticky",
                        "",
                        minimalArtifactsForDecisionLogMerge(),
                        List.of(),
                        false,
                        false,
                        List.of(),
                        new CoordinatorClarificationSettings(
                                CoordinatorClarificationMode.CANONICAL_V1, List.of(alwaysOpen))));
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        store.update(plan("ctx-merge-b", "p_merge_sticky", "the feature"));
        var action = new MergePlanningClarificationChoiceAction(store, reg);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("contextId", "ctx-merge-b");
        state.put("planningClarificationRaw", "yes");
        state.put(
                "planningClarificationMetaJson",
                metaJson(
                        "g_sticky",
                        "More detail?",
                        CanonicalMergeTargetPaths.defaultMergeTargetPath("g_sticky")));
        Map<String, Object> spread = (Map<String, Object>) action.run(null, state, Map.of("contextId", "ctx-merge-b"));
        assertEquals("true", spread.get("planningClarificationMergeOk"));
        assertEquals("false", spread.get("planningCanonicalUserInputRequired"));
        assertFalse(spread.containsKey("planningUserInputRequired"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void canonicalV1MergeRejectsGapIdWhenMergeTargetPathDoesNotMatchContract() {
        WorkProfileRegistry reg = new WorkProfileRegistry();
        reg.register(
                new WorkProfileDefinition(
                        "p_merge_mismatch",
                        "",
                        minimalArtifactsForDecisionLogMerge(),
                        List.of(),
                        false,
                        false,
                        List.of(),
                        new CoordinatorClarificationSettings(CoordinatorClarificationMode.CANONICAL_V1, List.of())));
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        store.update(plan("ctx-merge-mismatch", "p_merge_mismatch", "hello"));
        var action = new MergePlanningClarificationChoiceAction(store, reg);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("contextId", "ctx-merge-mismatch");
        state.put("planningClarificationRaw", "yes");
        state.put(
                "planningClarificationMetaJson",
                metaJson("g1", "Q?", "decision_log/decisions/wrong_field"));
        Map<String, Object> spread = (Map<String, Object>) action.run(null, state, Map.of("contextId", "ctx-merge-mismatch"));
        assertEquals("false", spread.get("planningClarificationMergeOk"));
        assertTrue(spread.get("planningClarificationMergeError").toString().contains("does not match"));
        assertFalse(
                store.getByContextId("ctx-merge-mismatch").orElseThrow().getIssues().stream()
                        .anyMatch(i -> i.getTitle() != null && i.getTitle().contains("Coordinator gap resolution")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void canonicalV1MergeAddsExplicitResolutionMarkerForAskedGap() {
        CoordinatorClarificationGapRule granularity =
                new CoordinatorClarificationGapRule(
                        "model_override_granularity",
                        true,
                        "Per step or step type?",
                        List.of("override", "step"),
                        List.of("model", "step"),
                        List.of("named steps", "per step", "step types", "both"));
        WorkProfileRegistry reg = new WorkProfileRegistry();
        reg.register(
                new WorkProfileDefinition(
                        "p_merge_resolution",
                        "",
                        minimalArtifactsForDecisionLogMerge(),
                        List.of(),
                        false,
                        false,
                        List.of(),
                        new CoordinatorClarificationSettings(
                                CoordinatorClarificationMode.CANONICAL_V1, List.of(granularity))));
        FeaturePlanStateStore store = new FeaturePlanStateStore();
        store.update(plan("ctx-merge-c", "p_merge_resolution", "route models by workflow step"));
        var action = new MergePlanningClarificationChoiceAction(store, reg);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("contextId", "ctx-merge-c");
        state.put("planningClarificationRaw", "scope is for individual steps");
        state.put(
                "planningClarificationMetaJson",
                metaJson(
                        "model_override_granularity",
                        "Per step or step type?",
                        CanonicalMergeTargetPaths.defaultMergeTargetPath("model_override_granularity")));

        Map<String, Object> spread = (Map<String, Object>) action.run(null, state, Map.of("contextId", "ctx-merge-c"));

        assertEquals("true", spread.get("planningClarificationMergeOk"));
        assertEquals("false", spread.get("planningCanonicalUserInputRequired"));
        assertFalse(spread.containsKey("planningUserInputRequired"));
        FeaturePlanState refreshed = store.getByContextId("ctx-merge-c").orElseThrow();
        String decisionBlob =
                PlanningArtifactTexts.allRepeatableFieldLines(refreshed, "decision_log", "decisions", "decision_text");
        assertTrue(
                decisionBlob.contains("Coordinator gap resolution (model_override_granularity): scope is for individual steps"),
                decisionBlob);
    }

    private static FeaturePlanState plan(String contextId, String profileId, String request) {
        return new FeaturePlanState(
                contextId,
                "f1",
                "slug",
                "room",
                "thread",
                null,
                "t",
                request,
                "PLANNING",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                FeaturePlanState.initialSectionStatuses(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                profileId,
                minimalPlanArtifacts(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
    }

    private static String metaJson(String gapId, String questionText, String mergeTargetPath) {
        return "{\"gapId\":\""
                + gapId
                + "\",\"questionText\":\""
                + questionText.replace("\"", "\\\"")
                + "\",\"mergeTargetPath\":\""
                + mergeTargetPath
                + "\"}";
    }

    private static List<ArtifactDefinition> minimalArtifactsForDecisionLogMerge() {
        return List.of(
                new ArtifactDefinition(
                        "decision_log",
                        "Decision log",
                        List.of(),
                        false,
                        List.of(
                                new SectionDefinition(
                                        "decisions",
                                        "Decisions",
                                        true,
                                        false,
                                        List.of(
                                                new FieldDefinition(
                                                        "decision_text", "Decision", "text", false, ""))))));
    }

    private static Map<String, ArtifactState> minimalPlanArtifacts() {
        return Map.of(
                "decision_log",
                new ArtifactState(
                        "decision_log",
                        Map.of(
                                "decisions",
                                new SectionState("decisions", SectionState.STATUS_EMPTY, Map.of(), List.of()))));
    }
}
