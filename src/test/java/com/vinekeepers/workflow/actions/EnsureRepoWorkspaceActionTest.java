package com.vinekeepers.workflow.actions;

import com.vinekeepers.bot.BotCatalog;
import com.vinekeepers.bot.BotDefinition;
import com.vinekeepers.bot.MemoryPolicy;
import com.vinekeepers.bot.ModelProfile;
import com.vinekeepers.bot.Persona;
import com.vinekeepers.bot.ToolPolicy;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.repo.DefaultRepoRefResolver;
import com.vinekeepers.state.repo.RepoWorkspaceProgressPhase;
import com.vinekeepers.state.repo.RepoWorkspaceService;
import com.vinekeepers.state.repo.RepoWorkspaceStateStore;
import com.vinekeepers.state.repo.RepoWorkspaceStatus;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EnsureRepoWorkspaceActionTest {

    private static BotCatalog sampleCatalog() {
        BotCatalog c = new BotCatalog();
        c.replaceAll(List.of(new BotDefinition(
                "coord1",
                new Persona("RepoBot", "p"),
                new ModelProfile("openai", "gpt-4o"),
                ToolPolicy.allowAll(),
                new MemoryPolicy(1024))));
        return c;
    }

    @Test
    void linksPlanStateAfterEnsure(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(gitWorks());
        Path repo = tmp.resolve("r");
        Files.createDirectories(repo);
        assertEquals(0, runGit(repo, "init"));
        assertEquals(0, runGit(repo, "config", "user.email", "t@t.c"));
        assertEquals(0, runGit(repo, "config", "user.name", "t"));
        Files.writeString(repo.resolve("a.txt"), "1");
        assertEquals(0, runGit(repo, "add", "a.txt"));
        assertEquals(0, runGit(repo, "commit", "-m", "i"));

        FeaturePlanStateStore plans = new FeaturePlanStateStore();
        FeaturePlanState plan = new FeaturePlanState(
                "ctx-1",
                "f1",
                "s",
                "room",
                null,
                null,
                "t",
                null,
                "PLANNING",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                FeaturePlanState.initialSectionStatuses(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                java.util.Map.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        plans.put(plan);

        RepoWorkspaceService svc = new RepoWorkspaceService(tmp.resolve("w"), false, new DefaultRepoRefResolver());
        RepoWorkspaceStateStore rwStore = new RepoWorkspaceStateStore();
        EnsureRepoWorkspaceAction action = new EnsureRepoWorkspaceAction(svc, rwStore, plans, new FeatureRoomStateStore(),
                new BotCatalog(), (ExplicitBotSender) null);

        Object r = action.run(null, Map.of("contextId", "ctx-1", "project", repo.toAbsolutePath().toString()), Map.of());
        assertEquals("OK", r);

        assertTrue(rwStore.getByContextId("ctx-1").isPresent());
        assertEquals(RepoWorkspaceStatus.RESOLVED_LOCAL, rwStore.getByContextId("ctx-1").get().getStatus());

        FeaturePlanState updated = plans.getByContextId("ctx-1").orElseThrow();
        assertEquals(RepoWorkspaceStatus.RESOLVED_LOCAL.name(), updated.getRepoWorkspaceStatus());
        assertNotNull(updated.getRepoLocalPath());
        assertNotNull(updated.getRepoWorkspaceId());
    }

    @Test
    void returnsErrorWhenRepoMissing() {
        RepoWorkspaceService svc = new RepoWorkspaceService(Path.of(System.getProperty("java.io.tmpdir")), false, new DefaultRepoRefResolver());
        EnsureRepoWorkspaceAction action = new EnsureRepoWorkspaceAction(svc, new RepoWorkspaceStateStore(), new FeaturePlanStateStore(), new FeatureRoomStateStore(),
                new BotCatalog(), (ExplicitBotSender) null);
        Object r = action.run(null, Map.of("contextId", "c"), Map.of());
        assertEquals("Missing repo input for ensure_repo_workspace.", r);
    }

    @Test
    void formatProgressChatLine_skipsNoisePhases() {
        assertNull(EnsureRepoWorkspaceAction.formatProgressChatLine(RepoWorkspaceProgressPhase.RESOLVED_REF, "x"));
        assertNull(EnsureRepoWorkspaceAction.formatProgressChatLine(RepoWorkspaceProgressPhase.VERIFYING_GIT, null));
        assertNotNull(EnsureRepoWorkspaceAction.formatProgressChatLine(RepoWorkspaceProgressPhase.CLONING, null));
    }

    @Test
    void formatProgressChatLine_readyClonedUsesCompactCopy() {
        assertEquals(
                "**Coordinator:** Repo ready: `main @ ba83567`",
                EnsureRepoWorkspaceAction.formatProgressChatLine(
                        RepoWorkspaceProgressPhase.READY_CLONED, "main @ ba83567"));
    }

    @Test
    void postsProgressWhenExplicitSenderAndTargetsPresent(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(gitWorks());
        Path repo = tmp.resolve("r");
        Files.createDirectories(repo);
        assertEquals(0, runGit(repo, "init"));
        assertEquals(0, runGit(repo, "config", "user.email", "t@t.c"));
        assertEquals(0, runGit(repo, "config", "user.name", "t"));
        Files.writeString(repo.resolve("a.txt"), "1");
        assertEquals(0, runGit(repo, "add", "a.txt"));
        assertEquals(0, runGit(repo, "commit", "-m", "i"));

        FeaturePlanStateStore plans = new FeaturePlanStateStore();
        FeaturePlanState plan = new FeaturePlanState(
                "ctx-1",
                "f1",
                "s",
                "room",
                null,
                null,
                "t",
                null,
                "PLANNING",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                FeaturePlanState.initialSectionStatuses(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                java.util.Map.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        plans.put(plan);

        List<String> messages = new ArrayList<>();
        AtomicReference<String> lastChannel = new AtomicReference<>();
        ExplicitBotSender sender = (channelId, messageId, content, botId) -> {
            lastChannel.set(channelId);
            messages.add(content);
            assertEquals("coord1", botId);
            return Optional.empty();
        };

        RepoWorkspaceService svc = new RepoWorkspaceService(tmp.resolve("w"), false, new DefaultRepoRefResolver());
        EnsureRepoWorkspaceAction action = new EnsureRepoWorkspaceAction(
                svc, new RepoWorkspaceStateStore(), plans, new FeatureRoomStateStore(), sampleCatalog(), sender);

        Object r = action.run(null, Map.of(
                "contextId", "ctx-1",
                "project", repo.toAbsolutePath().toString(),
                "channelId", "room-ch",
                "deliveryChannelId", "thread-ch",
                "__botId", "coord1"), Map.of());
        assertEquals("OK", r);
        assertEquals("thread-ch", lastChannel.get());
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("Using local repository"), messages.get(0));
        assertTrue(messages.get(0).contains("RepoBot:"), messages.get(0));
    }

    private static boolean gitWorks() {
        try {
            Process p = new ProcessBuilder("git", "--version").start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static int runGit(Path cwd, String... args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add("git");
        for (String a : args) {
            cmd.add(a);
        }
        pb.command(cmd);
        pb.directory(cwd.toFile());
        Process p = pb.start();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS));
        return p.exitValue();
    }
}
