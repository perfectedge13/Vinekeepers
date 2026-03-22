package com.vinekeepers.workflow.convergence;

import com.vinekeepers.workflow.viewmodel.UserCopyContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisibleDeltaTrackerTest {

    @Test
    void fingerprintChangesWhenSliceChanges() {
        Map<String, Object> a = Map.of("coordinatorSummary", "one", "progressLine", "p");
        Map<String, Object> b = Map.of("coordinatorSummary", "two", "progressLine", "p");
        String fa = VisibleDeltaTracker.fingerprintSlice(a, UserCopyContext.DEFAULT_COORDINATOR_KEYS);
        String fb = VisibleDeltaTracker.fingerprintSlice(b, UserCopyContext.DEFAULT_COORDINATOR_KEYS);
        assertTrue(VisibleDeltaTracker.hasDelta(fa, fb));
    }

    @Test
    void fingerprintStableForSameContent() {
        Map<String, Object> a = Map.of("coordinatorSummary", "same");
        String fa = VisibleDeltaTracker.fingerprintSlice(a, UserCopyContext.DEFAULT_COORDINATOR_KEYS);
        String fb = VisibleDeltaTracker.fingerprintSlice(a, UserCopyContext.DEFAULT_COORDINATOR_KEYS);
        assertFalse(VisibleDeltaTracker.hasDelta(fa, fb));
    }
}
