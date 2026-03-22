package com.vinekeepers.state.workflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressDedupeHelperTest {

    @Test
    void normalizeUnifiesNewlines() {
        assertEquals("a\nb", ProgressDedupeHelper.normalizeProgressBody("a\r\nb  "));
    }

    @Test
    void postWorthyWhenHashChanges() {
        String fp1 = ProgressDedupeHelper.fingerprintForBody("line a");
        String fp2 = ProgressDedupeHelper.fingerprintForBody("line b");
        assertNotEquals(fp1, fp2);
        assertTrue(ProgressDedupeHelper.isPostWorthy(fp1, fp2));
    }

    @Test
    void postNotWorthyWhenSameFingerprint() {
        String fp = ProgressDedupeHelper.fingerprintForBody("x");
        assertFalse(ProgressDedupeHelper.isPostWorthy(fp, fp));
    }

    @Test
    void firstPostAlwaysWorthyWithBlankLast() {
        assertTrue(ProgressDedupeHelper.isPostWorthy(null, "abc"));
    }
}
