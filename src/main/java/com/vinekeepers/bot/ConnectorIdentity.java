package com.vinekeepers.bot;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable connector-scoped identity for a bot.
 * Holds connector-specific attributes (e.g. tokenEnvKey, handlesOwnedSpaces for Discord)
 * so adapters can read known keys without the core depending on connector names.
 */
public final class ConnectorIdentity {

    private final Map<String, Object> attributes;

    public ConnectorIdentity(Map<String, Object> attributes) {
        this.attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
    }

    /**
     * Returns the value for the given attribute key, or null if absent.
     */
    public Object getAttribute(String key) {
        return key != null ? attributes.get(key) : null;
    }

    /**
     * Returns the string value for the key, or null if absent or not a string.
     * Non-string values are converted via toString() unless null.
     */
    public String getString(String key) {
        Object v = getAttribute(key);
        if (v == null) return null;
        if (v instanceof String s) return s.isBlank() ? null : s.trim();
        String s = v.toString();
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * Returns the boolean value for the key. Absent or null is false;
     * Boolean.TRUE is true; for other values, Boolean.parseBoolean(toString()).
     */
    public boolean getBoolean(String key) {
        Object v = getAttribute(key);
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }

    /**
     * Returns an immutable copy of all attributes.
     */
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConnectorIdentity that = (ConnectorIdentity) o;
        return Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attributes);
    }
}
