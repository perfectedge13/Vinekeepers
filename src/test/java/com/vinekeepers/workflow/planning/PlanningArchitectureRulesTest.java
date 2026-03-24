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

import java.util.Arrays;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AnalyzeClasses(packages = "com.vinekeepers", importOptions = ImportOption.DoNotIncludeTests.class)
public final class PlanningArchitectureRulesTest {

    @ArchTest
    static final ArchRule workflowActionsDoNotEnumerateCoordinatorGaps =
            classes()
                    .that()
                    .resideInAPackage("com.vinekeepers.workflow.actions")
                    .should(
                            new ArchCondition<>("not call CoordinatorClarificationGapEvaluator.evaluateOpenGaps") {
                                @Override
                                public void check(JavaClass javaClass, ConditionEvents events) {
                                    for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
                                        if (!"evaluateOpenGaps".equals(call.getName())) {
                                            continue;
                                        }
                                        if (!call.getTargetOwner().isEquivalentTo(CoordinatorClarificationGapEvaluator.class)) {
                                            continue;
                                        }
                                        events.add(
                                                SimpleConditionEvent.violated(
                                                        call,
                                                        javaClass.getFullName()
                                                                + " must not call CoordinatorClarificationGapEvaluator.evaluateOpenGaps"));
                                    }
                                }
                            });

    @Test
    void planningPostDraftGovernorHasNoDeriveMethod() {
        assertTrue(
                Arrays.stream(PlanningPostDraftGovernor.class.getDeclaredMethods())
                        .noneMatch(m -> "derive".equals(m.getName())));
    }
}
