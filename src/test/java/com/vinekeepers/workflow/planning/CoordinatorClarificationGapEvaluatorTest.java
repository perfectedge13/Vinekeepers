package com.vinekeepers.workflow.planning;

import com.vinekeepers.profile.CoordinatorClarificationGapRule;
import com.vinekeepers.profile.CoordinatorClarificationMode;
import com.vinekeepers.profile.CoordinatorClarificationSettings;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.PlanAssumption;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoordinatorClarificationGapEvaluatorTest {

    @Test
    void openWhenHintMatchesAndNotResolved() {
        CoordinatorClarificationSettings settings =
                new CoordinatorClarificationSettings(
                        CoordinatorClarificationMode.CANONICAL_V1,
                        List.of(
                                new CoordinatorClarificationGapRule(
                                        "g1",
                                        false,
                                        "Question about config vs runtime?",
                                        List.of("config", "runtime"),
                                        List.of(),
                                        List.of("config-driven", "config only"))));
        FeaturePlanState plan = minimalPlan("ctx", "do something");
        List<CoordinatorClarificationGapEvaluator.OpenGap> open =
                CoordinatorClarificationGapEvaluator.evaluateOpenGaps(
                        plan, settings, List.of("Should we change config or runtime?"));
        assertEquals(1, open.size());
        assertEquals("g1", open.get(0).gapId());
    }

    @Test
    void noOpenWhenCanonicalResolvesViaAssumption() {
        CoordinatorClarificationSettings settings =
                new CoordinatorClarificationSettings(
                        CoordinatorClarificationMode.CANONICAL_V1,
                        List.of(
                                new CoordinatorClarificationGapRule(
                                        "g1",
                                        false,
                                        "Question?",
                                        List.of("config", "runtime"),
                                        List.of(),
                                        List.of("config-driven"))));
        FeaturePlanState plan =
                minimalPlan("ctx", "x")
                        .withAppendedAssumption(PlanAssumption.fromLegacyText("a1", "We go config-driven for this feature.", Instant.now()));
        List<CoordinatorClarificationGapEvaluator.OpenGap> open =
                CoordinatorClarificationGapEvaluator.evaluateOpenGaps(
                        plan, settings, List.of("config versus runtime?"));
        assertTrue(open.isEmpty());
    }

    @Test
    void staleLlmHintDoesNotReopenWhenPlanSatisfied() {
        CoordinatorClarificationSettings settings =
                new CoordinatorClarificationSettings(
                        CoordinatorClarificationMode.CANONICAL_V1,
                        List.of(
                                new CoordinatorClarificationGapRule(
                                        "g1",
                                        false,
                                        "Ask?",
                                        List.of("config", "runtime"),
                                        List.of(),
                                        List.of("decision (user"))));
        FeaturePlanState plan =
                minimalPlan("ctx", "req")
                        .withAppendedAssumption(
                                PlanAssumption.fromLegacyText("a1", "Decision (user choice A): config only", Instant.now()));
        List<CoordinatorClarificationGapEvaluator.OpenGap> open =
                CoordinatorClarificationGapEvaluator.evaluateOpenGaps(
                        plan,
                        settings,
                        List.of("Paraphrase: runtime vs config tradeoff?"));
        assertTrue(open.isEmpty());
    }

    @Test
    void staticIsFineInAssumptionResolvesConfigVsRuntimeGap() {
        CoordinatorClarificationSettings settings =
                new CoordinatorClarificationSettings(
                        CoordinatorClarificationMode.CANONICAL_V1,
                        List.of(
                                new CoordinatorClarificationGapRule(
                                        "config_vs_runtime_scope",
                                        false,
                                        "Config or runtime?",
                                        List.of("config", "runtime"),
                                        List.of(),
                                        List.of(
                                                "config-driven",
                                                "static",
                                                "static is fine",
                                                "compile-time",
                                                "schema only"))));
        FeaturePlanState plan =
                minimalPlan("ctx", "x")
                        .withAppendedAssumption(
                                PlanAssumption.fromLegacyText("a1", "Static is fine for this piece.", Instant.now()));
        List<CoordinatorClarificationGapEvaluator.OpenGap> open =
                CoordinatorClarificationGapEvaluator.evaluateOpenGaps(
                        plan, settings, List.of("config versus runtime?"));
        assertTrue(open.isEmpty());
    }

    @Test
    void genericQuestionTemplateIsNeverSurfacedEvenWhenRuleWouldTrigger() {
        CoordinatorClarificationSettings settings =
                new CoordinatorClarificationSettings(
                        CoordinatorClarificationMode.CANONICAL_V1,
                        List.of(
                                new CoordinatorClarificationGapRule(
                                        "g_meta",
                                        false,
                                        "Please list any open questions you still have about this plan.",
                                        List.of("scope"),
                                        List.of(),
                                        List.of())));
        FeaturePlanState plan = minimalPlan("ctx", "unclear scope");
        List<CoordinatorClarificationGapEvaluator.OpenGap> open =
                CoordinatorClarificationGapEvaluator.evaluateOpenGaps(
                        plan, settings, List.of("We should clarify product scope"));
        assertTrue(open.isEmpty());
    }

    @Test
    void semanticGapsDisallowedReturnsNoOpenGaps() {
        CoordinatorClarificationSettings settings =
                new CoordinatorClarificationSettings(
                        CoordinatorClarificationMode.CANONICAL_V1,
                        List.of(
                                new CoordinatorClarificationGapRule(
                                        "g1",
                                        false,
                                        "Question about config vs runtime?",
                                        List.of("config", "runtime"),
                                        List.of(),
                                        List.of("config-driven", "config only"))));
        FeaturePlanState plan = minimalPlan("ctx", "do something");
        List<CoordinatorClarificationGapEvaluator.OpenGap> open =
                CoordinatorClarificationGapEvaluator.evaluateOpenGaps(
                        plan, settings, List.of("Should we change config or runtime?"), false);
        assertTrue(open.isEmpty());
    }

    @Test
    void narrowGapOpensFromCanonicalAllOfWhenFirstResolved() {
        CoordinatorClarificationSettings settings =
                new CoordinatorClarificationSettings(
                        CoordinatorClarificationMode.CANONICAL_V1,
                        List.of(
                                new CoordinatorClarificationGapRule(
                                        "wide",
                                        false,
                                        "Wide?",
                                        List.of("config", "runtime"),
                                        List.of(),
                                        List.of("both")),
                                new CoordinatorClarificationGapRule(
                                        "narrow",
                                        false,
                                        "Where do defaults live?",
                                        List.of(),
                                        List.of("both", "config", "runtime"),
                                        List.of("yaml", "bootstrap"))));
        FeaturePlanState plan =
                minimalPlan("ctx", "x")
                        .withAppendedAssumption(PlanAssumption.fromLegacyText("a1", "User chose both config and runtime.", Instant.now()));
        List<CoordinatorClarificationGapEvaluator.OpenGap> open =
                CoordinatorClarificationGapEvaluator.evaluateOpenGaps(plan, settings, List.of());
        assertEquals(1, open.size());
        assertEquals("narrow", open.get(0).gapId());
    }

    private static FeaturePlanState minimalPlan(String contextId, String request) {
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
                "software_feature_planning_v2",
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
