package com.vinekeepers.state.repo;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RepoWorkspaceServiceTest {

    private static boolean gitAvailable() {
        try {
            Process p = new ProcessBuilder("git", "--version").start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void git(Path cwd, String... args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add("git");
        for (String a : args) {
            cmd.add(a);
        }
        pb.command(cmd);
        pb.directory(cwd.toFile());
        pb.inheritIO();
        Process p = pb.start();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS));
        assertEquals(0, p.exitValue(), "git " + String.join(" ", args));
    }

    @Test
    void ensureExistingLocalGitRepo(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(gitAvailable());
        Path repo = tmp.resolve("myrepo");
        Files.createDirectories(repo);
        git(repo, "init");
        git(repo, "config", "user.email", "t@t.c");
        git(repo, "config", "user.name", "T");
        Files.writeString(repo.resolve("f.txt"), "x");
        git(repo, "add", "f.txt");
        git(repo, "commit", "-m", "init");

        RepoWorkspaceService svc = new RepoWorkspaceService(tmp.resolve("work"), false, new DefaultRepoRefResolver());
        RepoWorkspaceState s = svc.ensure("ctx-1", repo.toAbsolutePath().toString());
        assertEquals(RepoWorkspaceStatus.RESOLVED_LOCAL, s.getStatus());
        assertEquals(RepoMaterializationMode.EXISTING_LOCAL, s.getMaterializationMode());
        assertNotNull(s.getLocalPath());
        assertNotNull(s.getCommit());
    }

    @Test
    void ensureLocalInvokesProgressPhases(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(gitAvailable());
        Path repo = tmp.resolve("myrepo");
        Files.createDirectories(repo);
        git(repo, "init");
        git(repo, "config", "user.email", "t@t.c");
        git(repo, "config", "user.name", "T");
        Files.writeString(repo.resolve("f.txt"), "x");
        git(repo, "add", "f.txt");
        git(repo, "commit", "-m", "init");

        List<RepoWorkspaceProgressPhase> phases = new ArrayList<>();
        RepoWorkspaceService svc = new RepoWorkspaceService(tmp.resolve("work"), false, new DefaultRepoRefResolver());
        svc.ensure("ctx-p", repo.toAbsolutePath().toString(), (phase, detail) -> phases.add(phase));

        assertTrue(phases.contains(RepoWorkspaceProgressPhase.RESOLVED_REF));
        assertTrue(phases.contains(RepoWorkspaceProgressPhase.READY_LOCAL));
        assertFalse(phases.contains(RepoWorkspaceProgressPhase.CLONING));
    }

    @Test
    void ensureCloneDisabledReturnsUnavailable() {
        RepoWorkspaceService svc = new RepoWorkspaceService(Path.of(System.getProperty("java.io.tmpdir")), false, new DefaultRepoRefResolver());
        RepoWorkspaceState s = svc.ensure("ctx-2", "https://example.com/a/b");
        assertEquals(RepoWorkspaceStatus.UNAVAILABLE, s.getStatus());
        assertNotNull(s.getFailureReason());
    }
}
