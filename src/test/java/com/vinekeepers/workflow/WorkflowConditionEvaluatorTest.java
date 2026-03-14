package com.vinekeepers.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowConditionEvaluatorTest {

    @Test
    void equalsMatchesExact() {
        assertTrue(WorkflowConditionEvaluator.evaluate("hello", "equals", "hello", null));
        assertFalse(WorkflowConditionEvaluator.evaluate("hello", "equals", "world", null));
    }

    @Test
    void equalsDefaultWhenOperatorNull() {
        assertTrue(WorkflowConditionEvaluator.evaluate("x", null, "x", null));
    }

    @Test
    void not_equals() {
        assertTrue(WorkflowConditionEvaluator.evaluate("a", "not_equals", "b", null));
        assertFalse(WorkflowConditionEvaluator.evaluate("a", "not_equals", "a", null));
    }

    @Test
    void blankTrueWhenNull() {
        assertTrue(WorkflowConditionEvaluator.evaluate(null, "blank", null, null));
    }

    @Test
    void blankTrueWhenEmptyOrWhitespace() {
        assertTrue(WorkflowConditionEvaluator.evaluate("", "blank", null, null));
        assertTrue(WorkflowConditionEvaluator.evaluate("  ", "blank", null, null));
    }

    @Test
    void blankFalseWhenNonBlank() {
        assertFalse(WorkflowConditionEvaluator.evaluate("x", "blank", null, null));
    }

    @Test
    void nonblankTrueWhenNonBlank() {
        assertTrue(WorkflowConditionEvaluator.evaluate("x", "nonblank", null, null));
    }

    @Test
    void nonblankFalseWhenNullOrBlank() {
        assertFalse(WorkflowConditionEvaluator.evaluate(null, "nonblank", null, null));
        assertFalse(WorkflowConditionEvaluator.evaluate("", "nonblank", null, null));
    }

    @Test
    void containsTrueWhenSubstringPresent() {
        assertTrue(WorkflowConditionEvaluator.evaluate("hello world", "contains", "world", null));
    }

    @Test
    void containsFalseWhenSubstringAbsent() {
        assertFalse(WorkflowConditionEvaluator.evaluate("hello", "contains", "x", null));
    }

    @Test
    void containsFalseWhenStateNull() {
        assertFalse(WorkflowConditionEvaluator.evaluate(null, "contains", "x", null));
    }

    @Test
    void starts_withTrue() {
        assertTrue(WorkflowConditionEvaluator.evaluate("hello world", "starts_with", "hello", null));
    }

    @Test
    void starts_withFalse() {
        assertFalse(WorkflowConditionEvaluator.evaluate("hello", "starts_with", "world", null));
    }

    @Test
    void starts_withFalseWhenStateOrCompareNull() {
        assertFalse(WorkflowConditionEvaluator.evaluate(null, "starts_with", "h", null));
        assertFalse(WorkflowConditionEvaluator.evaluate("hi", "starts_with", null, null));
    }

    @Test
    void regexFullStringMatch() {
        assertTrue(WorkflowConditionEvaluator.evaluate("abc", "regex", "a.c", null));
        assertFalse(WorkflowConditionEvaluator.evaluate("abc", "regex", "^a$", null));
        assertTrue(WorkflowConditionEvaluator.evaluate("a", "regex", "^a$", null));
    }

    @Test
    void regexNullStateReturnsFalse() {
        assertFalse(WorkflowConditionEvaluator.evaluate(null, "regex", ".*", null));
    }

    @Test
    void regexInvalidPatternReturnsFalse() {
        assertFalse(WorkflowConditionEvaluator.evaluate("x", "regex", "[invalid", null));
    }

    @Test
    void one_ofMatchesWhenStateInList() {
        assertTrue(WorkflowConditionEvaluator.evaluate("b", "one_of", List.of("a", "b", "c"), null));
    }

    @Test
    void one_ofNoMatch() {
        assertFalse(WorkflowConditionEvaluator.evaluate("x", "one_of", List.of("a", "b"), null));
    }

    @Test
    void one_ofSingleStringTolerated() {
        assertTrue(WorkflowConditionEvaluator.evaluate("only", "one_of", "only", null));
    }

    @Test
    void one_ofEmptyListMatchesNullOrEmptyState() {
        assertTrue(WorkflowConditionEvaluator.evaluate(null, "one_of", List.of(), null));
        assertTrue(WorkflowConditionEvaluator.evaluate("", "one_of", List.of(), null));
    }

    @Test
    void transformAppliedToStateValueOnly() {
        // state "  YES  " with transform [trim, lower] -> "yes"; compareValue "yes" is not transformed
        assertTrue(WorkflowConditionEvaluator.evaluate("  YES  ", "equals", "yes", List.of("trim", "lower")));
        assertFalse(WorkflowConditionEvaluator.evaluate("  YES  ", "equals", "YES", List.of("trim", "lower")));
    }

    @Test
    void transformOnlyTrimLowerUpperApplied() {
        assertTrue(WorkflowConditionEvaluator.evaluate("  Hi  ", "equals", "hi", List.of("trim", "lower")));
    }
}
