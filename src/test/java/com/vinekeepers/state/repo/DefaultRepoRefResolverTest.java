package com.vinekeepers.state.repo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DefaultRepoRefResolverTest {

    private final DefaultRepoRefResolver resolver = new DefaultRepoRefResolver();

    @Test
    void resolvesHttpsWithoutGitSuffix() {
        assertEquals("https://github.com/a/b", resolver.resolve("https://github.com/a/b.git"));
    }

    @Test
    void resolvesOwnerRepoLowercase() {
        assertEquals("foo/bar", resolver.resolve("Foo/Bar"));
    }

    @Test
    void resolvesLocalDirectory(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("repo"));
        String abs = tmp.resolve("repo").toAbsolutePath().toString();
        String r = resolver.resolve(abs);
        assertNotNull(r);
        assertTrue(r.startsWith("local:"));
        assertTrue(r.contains("repo"));
    }

    @Test
    void nullOrBlankReturnsNull() {
        assertNull(resolver.resolve(null));
        assertNull(resolver.resolve("   "));
    }
}
