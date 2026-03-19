package com.vinekeepers.state.repo;

import com.vinekeepers.env.Env;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Resolves repo input to a local path: existing local repo, or shallow clone under workspace root when allowed.
 */
public final class RepoWorkspaceService {

    private final Path workspaceRoot;
    private final boolean allowGitClone;
    private final RepoRefResolver resolver;

    public RepoWorkspaceService(Path workspaceRoot, boolean allowGitClone, RepoRefResolver resolver) {
        this.workspaceRoot = workspaceRoot != null ? workspaceRoot : Path.of(System.getProperty("java.io.tmpdir"));
        this.allowGitClone = allowGitClone;
        this.resolver = resolver != null ? resolver : new DefaultRepoRefResolver();
    }

    public static RepoWorkspaceService fromEnv(RepoRefResolver resolver) {
        String root = Env.get("VINEKEEPERS_REPO_WORKSPACE_ROOT", System.getProperty("java.io.tmpdir"));
        boolean clone = Boolean.parseBoolean(Env.get("VINEKEEPERS_ALLOW_GIT_CLONE", "false"));
        return new RepoWorkspaceService(Path.of(root), clone, resolver);
    }

    /**
     * Ensure a workspace for the given context and raw repo input. Does not persist to store.
     */
    public RepoWorkspaceState ensure(String contextId, String rawRepoInput) {
        Objects.requireNonNull(contextId, "contextId");
        Instant now = Instant.now();
        String repoRef = resolver.resolve(rawRepoInput);
        if (repoRef == null || repoRef.isBlank()) {
            return new RepoWorkspaceState(
                    contextId,
                    null,
                    rawRepoInput,
                    null,
                    null,
                    null,
                    null,
                    RepoMaterializationMode.UNKNOWN,
                    RepoWorkspaceStatus.FAILED,
                    "Could not normalize repo reference",
                    now,
                    now);
        }
        String workspaceId = shortWorkspaceId(contextId, repoRef);

        if (repoRef.startsWith("local:")) {
            String pathStr = repoRef.substring("local:".length());
            Path localPath = Path.of(pathStr);
            if (!Files.isDirectory(localPath)) {
                return failed(contextId, repoRef, rawRepoInput, workspaceId, "Local path is not a directory: " + pathStr, now);
            }
            GitInfo info = readGitInfo(localPath);
            if (info == null) {
                return failed(contextId, repoRef, rawRepoInput, workspaceId, "Not a git repository: " + pathStr, now);
            }
            String absLocal = localPath.normalize().toAbsolutePath().toString().replace('\\', '/');
            return new RepoWorkspaceState(
                    contextId,
                    repoRef,
                    rawRepoInput,
                    workspaceId,
                    absLocal,
                    info.branch,
                    info.commit,
                    RepoMaterializationMode.EXISTING_LOCAL,
                    RepoWorkspaceStatus.RESOLVED_LOCAL,
                    null,
                    now,
                    now);
        }

        if (!allowGitClone) {
            return new RepoWorkspaceState(
                    contextId,
                    repoRef,
                    rawRepoInput,
                    workspaceId,
                    null,
                    null,
                    null,
                    RepoMaterializationMode.UNKNOWN,
                    RepoWorkspaceStatus.UNAVAILABLE,
                    "Git clone disabled (set VINEKEEPERS_ALLOW_GIT_CLONE=true)",
                    now,
                    now);
        }

        String cloneUrl = toCloneUrl(repoRef);
        if (cloneUrl == null) {
            return new RepoWorkspaceState(
                    contextId,
                    repoRef,
                    rawRepoInput,
                    workspaceId,
                    null,
                    null,
                    null,
                    RepoMaterializationMode.UNKNOWN,
                    RepoWorkspaceStatus.UNAVAILABLE,
                    "Unsupported repo reference for clone: " + repoRef,
                    now,
                    now);
        }

        try {
            Files.createDirectories(workspaceRoot);
        } catch (IOException e) {
            return failed(contextId, repoRef, rawRepoInput, workspaceId, "Could not create workspace root: " + e.getMessage(), now);
        }

        Path targetDir = workspaceRoot.resolve(workspaceId);
        try {
            if (Files.exists(targetDir)) {
                deleteRecursive(targetDir);
            }
        } catch (IOException e) {
            return failed(contextId, repoRef, rawRepoInput, workspaceId, "Could not clear target dir: " + e.getMessage(), now);
        }

        int code = runGit(localPathForProcess(workspaceRoot), "clone", "--depth", "1", cloneUrl, targetDir.toString());
        if (code != 0) {
            return failed(contextId, repoRef, rawRepoInput, workspaceId, "git clone failed with exit " + code, now);
        }

        GitInfo info = readGitInfo(targetDir);
        String branch = info != null ? info.branch : null;
        String commit = info != null ? info.commit : null;
        String abs = targetDir.normalize().toAbsolutePath().toString().replace('\\', '/');
        return new RepoWorkspaceState(
                contextId,
                repoRef,
                rawRepoInput,
                workspaceId,
                abs,
                branch,
                commit,
                RepoMaterializationMode.CLONED,
                RepoWorkspaceStatus.MATERIALIZED,
                null,
                now,
                now);
    }

    private static String shortWorkspaceId(String contextId, String repoRef) {
        UUID u = UUID.nameUUIDFromBytes((contextId + "|" + repoRef).getBytes(StandardCharsets.UTF_8));
        return "ws-" + u.toString().substring(0, 8);
    }

    private static RepoWorkspaceState failed(
            String contextId,
            String repoRef,
            String displayName,
            String workspaceId,
            String reason,
            Instant now) {
        return new RepoWorkspaceState(
                contextId,
                repoRef,
                displayName,
                workspaceId,
                null,
                null,
                null,
                RepoMaterializationMode.UNKNOWN,
                RepoWorkspaceStatus.FAILED,
                reason,
                now,
                now);
    }

    private static String toCloneUrl(String repoRef) {
        if (repoRef.startsWith("https://") || repoRef.startsWith("http://")) {
            return repoRef.endsWith(".git") ? repoRef : repoRef + ".git";
        }
        if (repoRef.startsWith("git@")) {
            return repoRef;
        }
        if (repoRef.contains("/") && !repoRef.contains("://") && !repoRef.startsWith("local:")) {
            String[] parts = repoRef.split("/");
            if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                return "https://github.com/" + parts[0] + "/" + parts[1] + ".git";
            }
        }
        return null;
    }

    private static GitInfo readGitInfo(Path repoRoot) {
        int ok = runGit(repoRoot, "rev-parse", "--is-inside-work-tree");
        if (ok != 0) {
            return null;
        }
        String branch = trimOrNull(runGitOutput(repoRoot, "rev-parse", "--abbrev-ref", "HEAD"));
        String commit = trimOrNull(runGitOutput(repoRoot, "rev-parse", "HEAD"));
        return new GitInfo(branch, commit);
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static int runGit(Path cwd, String... args) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.command(listWithGit(args));
            pb.directory(cwd.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return -1;
            }
            return p.exitValue();
        } catch (Exception e) {
            return -1;
        }
    }

    private static String runGitOutput(Path cwd, String... args) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.command(listWithGit(args));
            pb.directory(cwd.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            byte[] out = p.getInputStream().readAllBytes();
            boolean finished = p.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return null;
            }
            if (p.exitValue() != 0) {
                return null;
            }
            return new String(out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static java.util.List<String> listWithGit(String... args) {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(gitExecutable());
        for (String a : args) {
            cmd.add(a);
        }
        return cmd;
    }

    private static String gitExecutable() {
        String g = Env.get("GIT_EXECUTABLE", "git");
        return g;
    }

    private static Path localPathForProcess(Path p) {
        return Path.of(p.toAbsolutePath().toString());
    }

    private static void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }

    private static final class GitInfo {
        final String branch;
        final String commit;

        GitInfo(String branch, String commit) {
            this.branch = branch;
            this.commit = commit;
        }
    }
}
