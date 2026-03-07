package com.vinekeepers.env;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads a .env-style file into system properties (KEY=VALUE lines).
 * Only sets properties that are not already set, so environment/system wins.
 */
public final class EnvLoader {

    private static final Logger log = LoggerFactory.getLogger(EnvLoader.class);

    private EnvLoader() {}

    /**
     * Load the given file (e.g. ".env") and set each KEY=VALUE as a system property
     * if that key is not already set. Skips blank lines and lines starting with '#'.
     */
    public static void load(String path) {
        load(Path.of(path));
    }

    /**
     * Load the given path and set each KEY=VALUE as a system property if not already set.
     */
    public static void load(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int eq = trimmed.indexOf('=');
                if (eq <= 0) continue;
                String key = trimmed.substring(0, eq).trim();
                String value = eq < trimmed.length() - 1 ? trimmed.substring(eq + 1).trim() : "";
                if (key.isEmpty()) continue;
                if (System.getProperty(key) == null) {
                    System.setProperty(key, value);
                }
            }
        } catch (IOException e) {
            log.debug("Could not load env file {}: {}", path, e.getMessage());
        }
    }
}
