package com.vinekeepers.env;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EnvLoaderTest {

    private static final String PREFIX = "vinekeepers.test.env.";

    @AfterEach
    void clearTestProps() {
        System.clearProperty(PREFIX + "FOO");
        System.clearProperty(PREFIX + "BAR");
        System.clearProperty(PREFIX + "EMPTY");
    }

    @Test
    void loadSetsPropertiesFromFile(@TempDir Path dir) throws Exception {
        Path env = dir.resolve(".env");
        Files.writeString(env, PREFIX + "FOO=value1\n" + PREFIX + "BAR=value2\n");
        EnvLoader.load(env);
        assertEquals("value1", Env.get(PREFIX + "FOO", "default"));
        assertEquals("value2", Env.get(PREFIX + "BAR", "default"));
    }

    @Test
    void loadSkipsCommentsAndBlanks(@TempDir Path dir) throws Exception {
        Path env = dir.resolve(".env");
        Files.writeString(env, "# comment\n\n" + PREFIX + "FOO=ok\n  \n");
        EnvLoader.load(env);
        assertEquals("ok", Env.get(PREFIX + "FOO", "default"));
    }

    @Test
    void loadDoesNotOverwriteExistingProperty(@TempDir Path dir) throws Exception {
        System.setProperty(PREFIX + "FOO", "existing");
        Path env = dir.resolve(".env");
        Files.writeString(env, PREFIX + "FOO=newvalue\n");
        EnvLoader.load(env);
        assertEquals("existing", Env.get(PREFIX + "FOO", "default"));
    }

    @Test
    void loadMissingFileIsNoOp() {
        EnvLoader.load("nonexistent-.env-file-12345");
        assertNull(System.getProperty("nonexistent-.env-file-12345"));
    }

    @Test
    void envGetReturnsDefaultWhenMissing() {
        assertEquals("default", Env.get(PREFIX + "MISSING", "default"));
    }
}
