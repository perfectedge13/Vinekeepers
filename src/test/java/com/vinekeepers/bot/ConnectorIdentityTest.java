package com.vinekeepers.bot;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConnectorIdentity: getAttribute, getAttributes, immutability.
 */
class ConnectorIdentityTest {

    @Test
    void getAttribute_returnsValueWhenPresent() {
        ConnectorIdentity identity = new ConnectorIdentity(Map.of("tokenEnvKey", "MY_TOKEN", "handlesOwnedSpaces", true));
        assertEquals("MY_TOKEN", identity.getAttribute("tokenEnvKey"));
        assertTrue(Boolean.TRUE.equals(identity.getAttribute("handlesOwnedSpaces")));
    }

    @Test
    void getAttribute_returnsNullWhenAbsent() {
        ConnectorIdentity identity = new ConnectorIdentity(Map.of("a", "b"));
        assertNull(identity.getAttribute("missing"));
    }

    @Test
    void getAttribute_returnsNullWhenKeyIsNull() {
        ConnectorIdentity identity = new ConnectorIdentity(Map.of("a", "b"));
        assertNull(identity.getAttribute(null));
    }

    @Test
    void getAttributes_returnsImmutableCopy() {
        Map<String, Object> input = new HashMap<>(Map.of("k", "v"));
        ConnectorIdentity identity = new ConnectorIdentity(input);
        Map<String, Object> attrs = identity.getAttributes();
        assertEquals(Map.of("k", "v"), attrs);
        assertThrows(UnsupportedOperationException.class, () -> attrs.put("x", "y"));
    }

    @Test
    void getAttributes_mutationOfInputDoesNotAffectIdentity() {
        Map<String, Object> input = new HashMap<>(Map.of("k", "v"));
        ConnectorIdentity identity = new ConnectorIdentity(input);
        input.put("k", "changed");
        assertEquals("v", identity.getAttribute("k"));
        assertEquals(Map.of("k", "v"), identity.getAttributes());
    }

    @Test
    void constructor_withNull_usesEmptyMap() {
        ConnectorIdentity identity = new ConnectorIdentity(null);
        assertTrue(identity.getAttributes().isEmpty());
        assertNull(identity.getAttribute("any"));
    }

    @Test
    void equals_andHashCode() {
        ConnectorIdentity a = new ConnectorIdentity(Map.of("x", 1));
        ConnectorIdentity b = new ConnectorIdentity(Map.of("x", 1));
        ConnectorIdentity c = new ConnectorIdentity(Map.of("x", 2));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, null);
        assertEquals(a, a);
    }
}
