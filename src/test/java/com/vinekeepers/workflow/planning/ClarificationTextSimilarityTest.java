package com.vinekeepers.workflow.planning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClarificationTextSimilarityTest {

    @Test
    void identicalStrings_one() {
        assertEquals(
                1.0,
                ClarificationTextSimilarity.clarificationSimilarity("Hello", "Hello"),
                1e-9);
    }

    @Test
    void caseInsensitiveMatch_one() {
        assertEquals(
                1.0,
                ClarificationTextSimilarity.clarificationSimilarity("Hello", "hello"),
                1e-9);
    }

    @Test
    void nullOperand_zero() {
        assertEquals(0.0, ClarificationTextSimilarity.clarificationSimilarity(null, "x"), 0.0);
        assertEquals(0.0, ClarificationTextSimilarity.clarificationSimilarity("x", null), 0.0);
    }
}
