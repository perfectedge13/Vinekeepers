package com.vinekeepers.workflow.planning;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertEquals;

@AnalyzeClasses(packages = "com.vinekeepers", importOptions = ImportOption.DoNotIncludeTests.class)
public final class PlanningArchitectureRulesTest {

    private static final List<String> LEGACY_PLANNING_AUTHORITIES = List.of(
            "com.vinekeepers.workflow.planning.CanonicalPlanningGapEngine",
            "com.vinekeepers.workflow.planning.CoordinatorClarificationGapEvaluator",
            "com.vinekeepers.workflow.planning.CanonicalGapResolutionPolicy",
            "com.vinekeepers.workflow.planning.PlanningConfidenceService",
            "com.vinekeepers.workflow.planning.PlanningGapEvaluator",
            "com.vinekeepers.workflow.planning.PlanningPostDraftGovernor",
            "com.vinekeepers.workflow.planning.PlanningPostDraftAction",
            "com.vinekeepers.workflow.planning.PlanningMaterialRoutingOutcome",
            "com.vinekeepers.workflow.planning.PlanningCycleEvaluationStage",
            "com.vinekeepers.workflow.actions.PlanningFinalizePlanningCycleAction",
            "com.vinekeepers.profile.CoordinatorClarificationEnginePolicy");

    @ArchTest
    static final ArchRule productionDoesNotReferenceDeletedPlanningAuthorities =
            classes()
                    .that()
                    .resideInAnyPackage("com.vinekeepers..")
                    .should(notReferenceDeletedPlanningTypes());

    private static ArchCondition<JavaClass> notReferenceDeletedPlanningTypes() {
        return new ArchCondition<JavaClass>("not reference deleted planning authorities or removed cleanup targets") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                if (LEGACY_PLANNING_AUTHORITIES.contains(javaClass.getFullName())) {
                    return;
                }
                for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
                    if (!LEGACY_PLANNING_AUTHORITIES.contains(call.getTargetOwner().getFullName())) {
                        continue;
                    }
                    events.add(
                            SimpleConditionEvent.violated(
                                    call,
                                    javaClass.getFullName()
                                            + " must not reference deleted planning type "
                                            + call.getTargetOwner().getFullName()));
                }
            }
        };
    }

    @Test
    void clarificationProjectionFromSelectionPreservesGapId() {
        CanonicalClarificationSelection sel =
                new CanonicalClarificationSelection(
                        true,
                        "gap-coord-1",
                        "decision_log/decisions/decision_text",
                        "Which region should host the primary database cluster for this feature?",
                        QuestionMode.OPEN,
                        true,
                        false,
                        "",
                        "[]",
                        "{}",
                        List.of());
        assertEquals("gap-coord-1", ClarificationProjection.fromSelection(sel).canonicalGapId());
    }

    @Test
    void canonicalDecisionSupportNoLongerDerivesFromEvaluation() {
        boolean present = java.util.Arrays.stream(PlanningCanonicalDecisionSupport.class.getDeclaredMethods())
                .anyMatch(method -> "normalizeFromEvaluation".equals(method.getName()));
        assertEquals(false, present);
    }

    @Test
    void planningEvaluationServiceDoesNotExposeLegacyResultType() {
        boolean present = java.util.Arrays.stream(PlanningEvaluationService.class.getDeclaredClasses())
                .anyMatch(type -> "PlanningEvaluationResult".equals(type.getSimpleName()));
        assertEquals(false, present);
    }
}
