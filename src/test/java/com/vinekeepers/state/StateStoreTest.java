package com.vinekeepers.state;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateStoreTest {

    private StateStore store;

    @BeforeEach
    void setUp() {
        store = new StateStore();
    }

    @Test
    void getReturnsEmptyWhenKeyMissing() {
        assertEquals(Optional.empty(), store.get("missing", String.class));
    }

    @Test
    void putAndGetRoundTrip() {
        store.put("k1", "v1");
        assertEquals(Optional.of("v1"), store.get("k1", String.class));
    }

    @Test
    void getReturnsEmptyWhenTypeMismatch() {
        store.put("k1", 42);
        assertEquals(Optional.empty(), store.get("k1", String.class));
    }

    @Test
    void removeRemovesKey() {
        store.put("k1", "v1");
        store.remove("k1");
        assertFalse(store.contains("k1"));
        assertEquals(Optional.empty(), store.get("k1", String.class));
    }

    @Test
    void containsReturnsTrueWhenKeyPresent() {
        store.put("k1", "v1");
        assertTrue(store.contains("k1"));
        assertFalse(store.contains("k2"));
    }

    @Test
    void keysReturnsStoredKeys() {
        store.put("k1", "v1");
        store.put("k2", 2);

        assertEquals(java.util.Set.of("k1", "k2"), store.keys());
    }
}
