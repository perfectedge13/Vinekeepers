package com.vinekeepers.state.repo;

import com.vinekeepers.env.Env;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Resolves repo input to a local path: existing local repo, or shallow clone under workspace root when allowed.
 */
public final class RepoWorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(RepoWorkspaceService.class);

    private static final int GIT_OUTPUT_CAP = 4096;
    private static final long GIT_TIMEOUT_SHORT_SEC = 120;
    private static final long GIT_TIMEOUT_CLONE_SEC = 300;

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
        return ensure(contextId, rawRepoInput, null);
    }

    /**
     * Ensure a workspace for the given context and raw repo input. Does not persist to store.
     *
     * @param progress optional milestone callback (e.g. Discord thread updates)
     */
    public RepoWorkspaceState ensure(String contextId, String rawRepoInput, RepoWorkspaceProgressCallback progress) {
        Objects.requireNonNull(contextId, "contextId");
        Instant now = Instant.now();
        String repoRef = resolver.resolve(rawRepoInput);
        if (repoRef == null || repoRef.isBlank()) {
            notify(progress, RepoWorkspaceProgressPhase.FAILED, "Could not normalize repo reference");
            log.warn("Repo workspace [{}]: could not normalize ref from input", contextId);
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
        notify(progress, RepoWorkspaceProgressPhase.RESOLVED_REF, repoRef);
        log.info("Repo workspace [{}]: resolved ref {}", contextId, safeRefForLog(repoRef));

        String workspaceId = shortWorkspaceId(contextId, repoRef);

        if (repoRef.startsWith("local:")) {
            return ensureLocal(contextId, rawRepoInput, repoRef, workspaceId, progress, now);
        }

        if (!allowGitClone) {
            String reason = "Git clone disabled (set VINEKEEPERS_ALLOW_GIT_CLONE=true)";
            notify(progress, RepoWorkspaceProgressPhase.FAILED, reason);
            log.warn("Repo workspace [{}]: {}", contextId, reason);
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
                    reason,
                    now,
                    now);
        }

        String cloneUrl = toCloneUrl(repoRef);
        if (cloneUrl == null) {
            String reason = "Unsupported repo reference for clone: " + repoRef;
            notify(progress, RepoWorkspaceProgressPhase.FAILED, reason);
            log.warn("Repo workspace [{}]: {}", contextId, reason);
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
                    reason,
                    now,
                    now);
        }

        try {
            notify(progress, RepoWorkspaceProgressPhase.CREATING_WORKSPACE_ROOT, null);
            log.info("Repo workspace [{}]: ensuring workspace root {}", contextId, workspaceRoot);
            Files.createDirectories(workspaceRoot);
        } catch (IOException e) {
            String reason = "Could not create workspace root: " + e.getMessage();
            notify(progress, RepoWorkspaceProgressPhase.FAILED, reason);
            log.warn("Repo workspace [{}]: {}", contextId, reason, e);
            return failed(contextId, repoRef, rawRepoInput, workspaceId, reason, now);
        }

        Path targetDir = workspaceRoot.resolve(workspaceId);
        try {
            if (Files.exists(targetDir)) {
                notify(progress, RepoWorkspaceProgressPhase.REMOVING_STALE_CLONE, targetDir.getFileName().toString());
                log.info("Repo workspace [{}]: removing stale workspace dir {}", contextId, targetDir);
                deleteRecursiveWithRetry(targetDir);
            }
        } catch (IOException e) {
            String reason = "Could not clear target dir: " + e.getMessage();
            notify(progress, RepoWorkspaceProgressPhase.FAILED, reason);
            log.warn("Repo workspace [{}]: {}", contextId, reason, e);
            return failed(contextId, repoRef, rawRepoInput, workspaceId, reason, now);
        }

        notify(progress, RepoWorkspaceProgressPhase.CLONING, null);
        log.info("Repo workspace [{}]: cloning into {}", contextId, targetDir);
        GitRunResult cloneResult = runGitWithOutput(
                localPathForProcess(workspaceRoot),
                GIT_TIMEOUT_CLONE_SEC,
                "clone",
                "--depth",
                "1",
                cloneUrl,
                targetDir.toString());
        if (cloneResult.exitCode() != 0) {
            String snippet = truncateForMessage(cloneResult.output());
            String reason = "git clone failed (exit " + cloneResult.exitCode() + ")" + (snippet.isBlank() ? "" : ": " + snippet);
            notify(progress, RepoWorkspaceProgressPhase.FAILED, reason);
            log.warn("Repo workspace [{}]: {}", contextId, reason);
            return failed(contextId, repoRef, rawRepoInput, workspaceId, reason, now);
        }

        notify(progress, RepoWorkspaceProgressPhase.VERIFYING_GIT, null);
        GitInfo info = readGitInfo(targetDir);
        String branch = info != null ? info.branch : null;
        String commit = info != null ? info.commit : null;
        String abs = targetDir.normalize().toAbsolutePath().toString().replace('\\', '/');
        String readyDetail = formatReadyCloned(branch, commit);
        notify(progress, RepoWorkspaceProgressPhase.READY_CLONED, readyDetail);
        log.info("Repo workspace [{}]: materialized at {} ({})", contextId, abs, readyDetail);
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

    private RepoWorkspaceState ensureLocal(
            String contextId,
            String rawRepoInput,
            String repoRef,
            String workspaceId,
            RepoWorkspaceProgressCallback progress,
            Instant now) {
        String pathStr = repoRef.substring("local:".length());
        Path localPath = Path.of(pathStr);
        notify(progress, RepoWorkspaceProgressPhase.VERIFYING_GIT, null);
        if (!Files.isDirectory(localPath)) {
            String reason = "Local path is not a directory: " + pathStr;
            notify(progress, RepoWorkspaceProgressPhase.FAILED, reason);
            log.warn("Repo workspace [{}]: {}", contextId, reason);
            return failed(contextId, repoRef, rawRepoInput, workspaceId, reason, now);
        }
        GitInfo info = readGitInfo(localPath);
        if (info == null) {
            String reason = "Not a git repository: " + pathStr;
            notify(progress, RepoWorkspaceProgressPhase.FAILED, reason);
            log.warn("Repo workspace [{}]: {}", contextId, reason);
            return failed(contextId, repoRef, rawRepoInput, workspaceId, reason, now);
        }
        String absLocal = localPath.normalize().toAbsolutePath().toString().replace('\\', '/');
        notify(progress, RepoWorkspaceProgressPhase.READY_LOCAL, absLocal);
        log.info("Repo workspace [{}]: using local repo {}", contextId, absLocal);
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

    private static void notify(RepoWorkspaceProgressCallback progress, RepoWorkspaceProgressPhase phase, String detail) {
        if (progress != null) {
            progress.onProgress(phase, detail);
        }
    }

    private static String safeRefForLog(String repoRef) {
        if (repoRef == null) {
            return "?";
        }
        if (repoRef.startsWith("local:")) {
            return "local:<path>";
        }
        return repoRef;
    }

    private static String formatReadyCloned(String branch, String commit) {
        String b = branch != null ? branch : "?";
        String c = shortSha(commit);
        return b + " @ " + c;
    }

    private static String shortSha(String commit) {
        if (commit == null || commit.isBlank()) {
            return "?";
        }
        String t = commit.trim();
        return t.length() > 7 ? t.substring(0, 7) : t;
    }

    private static String truncateForMessage(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        String t = s.trim().replace("\r\n", "\n");
        if (t.length() <= GIT_OUTPUT_CAP) {
            return t;
        }
        return "…" + t.substring(t.length() - GIT_OUTPUT_CAP);
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

    private GitInfo readGitInfo(Path repoRoot) {
        int ok = runGitSimple(repoRoot, GIT_TIMEOUT_SHORT_SEC, "rev-parse", "--is-inside-work-tree");
        if (ok != 0) {
            return null;
        }
        String branch = trimOrNull(runGitOutputOnly(repoRoot, GIT_TIMEOUT_SHORT_SEC, "rev-parse", "--abbrev-ref", "HEAD"));
        String commit = trimOrNull(runGitOutputOnly(repoRoot, GIT_TIMEOUT_SHORT_SEC, "rev-parse", "HEAD"));
        return new GitInfo(branch, commit);
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static GitRunResult runGitWithOutput(Path cwd, long timeoutSec, String... args) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.command(listWithGit(args));
            pb.directory(cwd.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            byte[] out = p.getInputStream().readAllBytes();
            boolean finished = p.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return new GitRunResult(-1, "git timed out after " + timeoutSec + "s");
            }
            String text = new String(out, StandardCharsets.UTF_8);
            return new GitRunResult(p.exitValue(), text);
        } catch (Exception e) {
            return new GitRunResult(-1, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private static int runGitSimple(Path cwd, long timeoutSec, String... args) {
        GitRunResult r = runGitWithOutput(cwd, timeoutSec, args);
        return r.exitCode();
    }

    private static String runGitOutputOnly(Path cwd, long timeoutSec, String... args) {
        GitRunResult r = runGitWithOutput(cwd, timeoutSec, args);
        if (r.exitCode() != 0) {
            return null;
        }
        return r.output();
    }

    private static List<String> listWithGit(String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(gitExecutable());
        for (String a : args) {
            cmd.add(a);
        }
        return cmd;
    }

    private static String gitExecutable() {
        return Env.get("GIT_EXECUTABLE", "git");
    }

    private static Path localPathForProcess(Path p) {
        return Path.of(p.toAbsolutePath().toString());
    }

    private static void deleteRecursiveWithRetry(Path root) throws IOException {
        try {
            deleteRecursiveStrict(root);
        } catch (IOException e) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            deleteRecursiveStrict(root);
        }
    }

    /**
     * Deletes {@code root} and all descendants; throws if anything remains or any delete fails.
     */
    private static void deleteRecursiveStrict(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        IOException first = null;
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path p : paths) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    if (first == null) {
                        first = e;
                    }
                    log.debug("Delete failed for {}: {}", p, e.getMessage());
                }
            }
        }
        if (Files.exists(root)) {
            if (first != null) {
                throw new IOException("Could not fully remove " + root + ": " + first.getMessage(), first);
            }
            throw new IOException("Could not fully remove: " + root);
        }
    }

    private record GitRunResult(int exitCode, String output) {}

    private static final class GitInfo {
        final String branch;
        final String commit;

        GitInfo(String branch, String commit) {
            this.branch = branch;
            this.commit = commit;
        }
    }
}
