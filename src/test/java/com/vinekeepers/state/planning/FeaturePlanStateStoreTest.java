package com.vinekeepers.state.planning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FeaturePlanStateStoreTest {

    private FeaturePlanStateStore store;

    @BeforeEach
    void setUp() {
        store = new FeaturePlanStateStore();
    }

    @Test
    void putAndGetByContextId() {
        FeaturePlanState p = new FeaturePlanState(
                "ctx-1",
                "feat-1",
                "slug",
                "room-1",
                "thread-1",
                "owner/repo",
                "Title",
                "change",
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
                null,
                Map.of(),
                null,
                null);
        store.put(p);
        assertTrue(store.getByContextId("ctx-1").isPresent());
        assertEquals("feat-1", store.getByContextId("ctx-1").get().getFeatureId());
        assertTrue(store.getByFeatureId("feat-1").isPresent());
        assertTrue(store.getByRoomChannelId("room-1").isPresent());
        assertTrue(store.getByIntakeThreadId("thread-1").isPresent());
    }

    @Test
    void updateReindexesFeatureId() {
        FeaturePlanState p = new FeaturePlanState(
                "ctx-1",
                "feat-a",
                "s",
                "room-1",
                null,
                null,
                "t",
                null,
                "PLANNING",
                null,
                null,
                null,
                null,
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
                null,
                Map.of(),
                null,
                null);
        store.put(p);
        FeaturePlanState p2 = new FeaturePlanState(
                "ctx-1",
                "feat-b",
                "s",
                "room-1",
                null,
                null,
                "t",
                null,
                "PLANNING",
                null,
                null,
                null,
                null,
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
                null,
                Map.of(),
                null,
                null);
        store.update(p2);
        assertTrue(store.getByFeatureId("feat-b").isPresent());
        assertTrue(store.getByFeatureId("feat-a").isEmpty());
    }
}
