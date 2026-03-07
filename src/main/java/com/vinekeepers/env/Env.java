package com.vinekeepers.env;

import java.util.Objects;

/**
 * Reads configuration from system properties (after EnvLoader) or environment, with optional default.
 */
public final class Env {

    private Env() {}

    /**
     * Get value for key: system property first, then environment variable, then default.
     */
    public static String get(String key, String defaultValue) {
        Objects.requireNonNull(key, "key");
        String v = System.getProperty(key);
        if (v != null && !v.isEmpty()) return v;
        v = System.getenv(key);
        if (v != null && !v.isEmpty()) return v;
        return defaultValue;
    }
}
