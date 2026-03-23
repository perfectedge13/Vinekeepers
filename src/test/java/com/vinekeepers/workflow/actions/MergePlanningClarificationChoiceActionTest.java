package com.vinekeepers.workflow.actions;

import com.vinekeepers.profile.CoordinatorClarificationGapRule;
import com.vinekeepers.profile.CoordinatorClarificationMode;
import com.vinekeepers.profile.CoordinatorClarificationSettings;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                        List.of(),
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
        state.put("planningClarificationMetaJson", "{\"gapId\":\"g1\",\"questionText\":\"Q?\"}");
        Map<String, Object> spread = (Map<String, Object>) action.run(null, state, Map.of("contextId", "ctx-merge-a"));
        assertEquals("true", spread.get("planningClarificationMergeOk"));
        assertEquals("false", spread.get("planningUserInputRequired"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void canonicalV1MergeSetsUserInputTrueWhenGapStillOpen() {
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
                        List.of(),
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
        state.put("planningClarificationMetaJson", "{\"gapId\":\"g_sticky\",\"questionText\":\"More detail?\"}");
        Map<String, Object> spread = (Map<String, Object>) action.run(null, state, Map.of("contextId", "ctx-merge-b"));
        assertEquals("true", spread.get("planningClarificationMergeOk"));
        assertEquals("true", spread.get("planningUserInputRequired"));
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
                        List.of(),
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
                "{\"gapId\":\"model_override_granularity\",\"questionText\":\"Per step or step type?\"}");

        Map<String, Object> spread = (Map<String, Object>) action.run(null, state, Map.of("contextId", "ctx-merge-c"));

        assertEquals("true", spread.get("planningClarificationMergeOk"));
        assertEquals("false", spread.get("planningUserInputRequired"));
        FeaturePlanState refreshed = store.getByContextId("ctx-merge-c").orElseThrow();
        assertTrue(
                refreshed.getAssumptions().stream()
                        .map(assumption -> assumption.getStatement() != null ? assumption.getStatement() : "")
                        .anyMatch(text -> text.contains("Coordinator gap resolution (model_override_granularity): per step.")));
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
                Map.of(),
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
}
