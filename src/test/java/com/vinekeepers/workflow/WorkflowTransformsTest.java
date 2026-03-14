package com.vinekeepers.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowTransformsTest {

    @Test
    void trimReturnsTrimmedString() {
        assertEquals("abc", WorkflowTransforms.trim("  abc  "));
        assertEquals("x", WorkflowTransforms.trim("x"));
    }

    @Test
    void trimNullReturnsNull() {
        assertNull(WorkflowTransforms.trim(null));
    }

    @Test
    void lowerReturnsLowercase() {
        assertEquals("hello", WorkflowTransforms.lower("Hello"));
        assertEquals("mixed", WorkflowTransforms.lower("MIXED"));
    }

    @Test
    void lowerNullReturnsNull() {
        assertNull(WorkflowTransforms.lower(null));
    }

    @Test
    void upperReturnsUppercase() {
        assertEquals("HELLO", WorkflowTransforms.upper("hello"));
        assertEquals("MIXED", WorkflowTransforms.upper("Mixed"));
    }

    @Test
    void upperNullReturnsNull() {
        assertNull(WorkflowTransforms.upper(null));
    }

    @Test
    void defaultTransformReturnsValueWhenNonBlank() {
        assertEquals("ok", WorkflowTransforms.defaultTransform("ok", "fallback"));
    }

    @Test
    void defaultTransformReturnsDefaultWhenNull() {
        assertEquals("fallback", WorkflowTransforms.defaultTransform(null, "fallback"));
    }

    @Test
    void defaultTransformReturnsDefaultWhenBlank() {
        assertEquals("fallback", WorkflowTransforms.defaultTransform("  ", "fallback"));
        assertEquals("fallback", WorkflowTransforms.defaultTransform("", "fallback"));
    }

    @Test
    void defaultTransformNullDefaultReturnsEmptyWhenValueBlank() {
        assertEquals("", WorkflowTransforms.defaultTransform(null, null));
        assertEquals("", WorkflowTransforms.defaultTransform("", null));
    }

    @Test
    void applyOneTrim() {
        assertEquals("x", WorkflowTransforms.applyOne("  x  ", "trim", null));
    }

    @Test
    void applyOneLower() {
        assertEquals("hi", WorkflowTransforms.applyOne("HI", "lower", null));
    }

    @Test
    void applyOneUpper() {
        assertEquals("HI", WorkflowTransforms.applyOne("hi", "upper", null));
    }

    @Test
    void applyOneDefault() {
        assertEquals("def", WorkflowTransforms.applyOne(null, "default", "def"));
        assertEquals("def", WorkflowTransforms.applyOne("", "default", "def"));
        assertEquals("val", WorkflowTransforms.applyOne("val", "default", "def"));
    }

    @Test
    void applyOneUnknownReturnsValueAsIs() {
        assertEquals("x", WorkflowTransforms.applyOne("x", "unknown", null));
    }

    @Test
    void applyOneNullValueReturnsNullExceptForDefault() {
        assertNull(WorkflowTransforms.applyOne(null, "trim", null));
        assertEquals("d", WorkflowTransforms.applyOne(null, "default", "d"));
    }

    @Test
    void applyAllOrderWhenChainingTrimThenLower() {
        String value = "  Mixed CASE  ";
        List<String> transforms = List.of("trim", "lower");
        assertEquals("mixed case", WorkflowTransforms.applyAll(value, transforms, null));
    }

    @Test
    void applyAllOrderWhenChainingLowerThenUpper() {
        String value = "hello";
        List<String> transforms = List.of("lower", "upper");
        assertEquals("HELLO", WorkflowTransforms.applyAll(value, transforms, null));
    }

    @Test
    void applyAllWithDefaultInList() {
        assertEquals("fallback", WorkflowTransforms.applyAll(null, List.of("trim", "default"), "fallback"));
        assertEquals("val", WorkflowTransforms.applyAll("  val  ", List.of("trim", "default"), "x"));
    }

    @Test
    void applyAllNullOrEmptyListReturnsValue() {
        assertEquals("x", WorkflowTransforms.applyAll("x", null, null));
        assertEquals("x", WorkflowTransforms.applyAll("x", List.of(), null));
    }

    @Test
    void asStringCoercesObjectToString() {
        assertEquals("42", WorkflowTransforms.asString(42));
        assertEquals("true", WorkflowTransforms.asString(true));
    }

    @Test
    void asStringNullReturnsNull() {
        assertNull(WorkflowTransforms.asString(null));
    }

    @Test
    void isBlankTrueForNull() {
        assertTrue(WorkflowTransforms.isBlank(null));
    }

    @Test
    void isBlankTrueForEmptyOrWhitespace() {
        assertTrue(WorkflowTransforms.isBlank(""));
        assertTrue(WorkflowTransforms.isBlank("  "));
    }

    @Test
    void isBlankFalseForNonBlank() {
        assertFalse(WorkflowTransforms.isBlank("x"));
        assertFalse(WorkflowTransforms.isBlank("  x  "));
    }
}
