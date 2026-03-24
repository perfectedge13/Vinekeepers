package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.TestWorkProfiles;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.planning.PlanningFailureCategory;
import com.vinekeepers.state.workflow.UnresolvedItemLedger;
import com.vinekeepers.workflow.actions.InitializeFeaturePlanStateAction;
import com.vinekeepers.workflow.planning.PlanningQuestionRankingPolicy.RankedClarification;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanningCyclePipelineFailureHandlingTest {

    @Test
    void applyImmediateSynthesisFailure_blankCategoryFallsBackToCycleError() throws Exception {
        TestContext ctx = createContext("ctx-immediate");
        Method m =
                PlanningCyclePipeline.class.getDeclaredMethod(
                        "applyImmediateSynthesisFailure", String.class, Map.class, Map.class);
        m.setAccessible(true);

        Map<String, Object> spread = new LinkedHashMap<>();
        Map<String, Object> synthSpread = new LinkedHashMap<>();
        synthSpread.put("planningLlmError", "Upsert requirements_spec/feature_summary does not match this planning profile.");
        synthSpread.put("planningLlmUpsertCount", "0");

        assertDoesNotThrow(() -> m.invoke(ctx.pipeline(), ctx.plan().getContextId(), spread, synthSpread));
        assertEquals("SYNTHESIS_UPSERTS_NOT_APPLIED", spread.get("planningRoomCycleError"));
    }

    @Test
    void applyImmediateSynthesisFailure_usesExplicitSynthesisCategoryWhenPresent() throws Exception {
        TestContext ctx = createContext("ctx-immediate-category");
        Method m =
                PlanningCyclePipeline.class.getDeclaredMethod(
                        "applyImmediateSynthesisFailure", String.class, Map.class, Map.class);
        m.setAccessible(true);

        Map<String, Object> spread = new LinkedHashMap<>();
        Map<String, Object> synthSpread = new LinkedHashMap<>();
        synthSpread.put("planningLlmError", "ERROR: upstream timeout");
        synthSpread.put("planningLlmUpsertCount", "0");
        synthSpread.put("planningSynthesisFailureCategory", PlanningFailureCategory.SYNTHESIS_TRANSPORT_ERROR.name());

        assertDoesNotThrow(() -> m.invoke(ctx.pipeline(), ctx.plan().getContextId(), spread, synthSpread));
        assertEquals(PlanningFailureCategory.SYNTHESIS_TRANSPORT_ERROR.name(), spread.get("planningSynthesisFailureCategory"));
        assertEquals(PlanningFailureCategory.SYNTHESIS_TRANSPORT_ERROR.name(), spread.get("planningRoomCycleError"));
    }

    @Test
    void applyPostDraftGovernor_blankCategoryWithCycleErrorDoesNotThrow() throws Exception {
        TestContext ctx = createContext("ctx-post-draft");
        Class<?> outcomeClass =
                Class.forName("com.vinekeepers.workflow.planning.PlanningCyclePipeline$ClarificationRoundOutcome");
        Constructor<?> ctor = outcomeClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object outcome =
                ctor.newInstance(
                        ctx.plan(),
                        new RankedClarification(false, "", "[]", "{}", 0, List.of(), false, ""),
                        new PlanningDeliberationLedgerSync.UpsertResult(UnresolvedItemLedger.empty(), Optional.empty()),
                        false,
                        false,
                        false,
                        "");
        Method m =
                PlanningCyclePipeline.class.getDeclaredMethod(
                        "applyPostDraftGovernor",
                        String.class,
                        Map.class,
                        Map.class,
                        FeaturePlanState.class,
                        outcomeClass,
                        RankedClarification.class,
                        WorkProfileDefinition.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        boolean.class,
                        String.class);
        m.setAccessible(true);

        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("planningRoomCycleError", "SYNTHESIS_UPSERTS_NOT_APPLIED");
        spread.put("planningSynthesisFailureCategory", "");

        assertDoesNotThrow(
                () ->
                        m.invoke(
                                ctx.pipeline(),
                                ctx.plan().getContextId(),
                                Map.of(),
                                spread,
                                ctx.plan(),
                                outcome,
                    new RankedClarification(false, "", "[]", "{}", 0, List.of(), false, ""),
                                ctx.profile(),
                                false,
                                false,
                                false,
                                false,
                                "thin"));
        assertEquals("BLOCK", spread.get(PlanningPostDraftGovernor.SPREAD_KEY));
        assertEquals("false", spread.get("planningReadyToPostPacket"));
    }

    private static TestContext createContext(String contextId) {
        FeaturePlanStateStore planStore = new FeaturePlanStateStore();
        WorkProfileRegistry registry = TestWorkProfiles.loadFromRepoConfig();
        InitializeFeaturePlanStateAction init =
                new InitializeFeaturePlanStateAction(planStore, new FeatureRoomStateStore(), registry);
        assertEquals(
                "OK",
                init.run(
                        null,
                        Map.of(
                                "contextId", contextId,
                                "channelId", "room-" + contextId,
                                "repoRef", "perfectedge13/Vinekeepers",
                                "initialRequest", "Improve planner stability"),
                        Map.of("profileId", "software_feature_planning_v2")));
        FeaturePlanState plan = planStore.getByContextId(contextId).orElseThrow();
        WorkProfileDefinition profile = registry.get("software_feature_planning_v2").orElseThrow();
        PlanningCyclePipeline pipeline = new PlanningCyclePipeline(null, planStore, registry);
        return new TestContext(pipeline, plan, profile);
    }

    private record TestContext(
            PlanningCyclePipeline pipeline, FeaturePlanState plan, WorkProfileDefinition profile) {}
}
