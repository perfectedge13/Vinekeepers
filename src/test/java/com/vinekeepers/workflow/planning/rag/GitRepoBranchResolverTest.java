package com.vinekeepers.workflow.planning.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GitRepoBranchResolverTest {

    @Test
    void bindBranchWinsOverState() {
        GitRepoBranchResolver r = new GitRepoBranchResolver();
        assertEquals(
                "from-bind",
                r.resolve(Path.of("."), Map.of("branch", "from-state"), Map.of("branch", "from-bind")));
    }

    @Test
    void gitBranchAliasAccepted() {
        GitRepoBranchResolver r = new GitRepoBranchResolver();
        assertEquals("alias", r.resolve(Path.of("."), Map.of(), Map.of("gitBranch", "alias")));
    }

    @Test
    void missingRepo_returnsUnknown(@TempDir Path empty) {
        GitRepoBranchResolver r = new GitRepoBranchResolver();
        assertEquals("unknown", r.resolve(empty.resolve("nope"), Map.of(), Map.of()));
    }
}
