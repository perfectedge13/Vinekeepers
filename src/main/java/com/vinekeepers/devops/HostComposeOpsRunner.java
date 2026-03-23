package com.vinekeepers.devops;

import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.env.Env;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Runs allowlisted {@code docker compose} (or Cursor Agent with a fixed prompt) for a {@link DeployTarget}.
 */
public final class HostComposeOpsRunner {

    private static final Logger log = LoggerFactory.getLogger(HostComposeOpsRunner.class);
    private static final Path DOCKER_SOCKET_PATH = Path.of("/var/run/docker.sock");

    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "host-compose-ops");
        t.setDaemon(true);
        return t;
    });

    private HostComposeOpsRunner() {
    }

    /**
     * @param serviceId manifest service name or {@code _all} for all configured services
     */
    public static void submit(OutboundDeliveryRouter router,
                              String workflowBotId,
                              String progressChannelId,
                              DeployTarget target,
                              ComposeOperation operation,
                              String serviceId) {
        Objects.requireNonNull(router, "router");
        if (workflowBotId == null || workflowBotId.isBlank()) {
            log.warn("Compose ops skipped: missing workflow bot id (__botId)");
            return;
        }
        String channel = progressChannelId != null ? progressChannelId : "";
        if (channel.isBlank()) {
            log.warn("Compose ops skipped: no progress channel id");
            return;
        }
        if (target == null || !target.getCompose().isConfigured()) {
            sendLine(router, workflowBotId, channel, "Compose ops aborted: target has no compose configuration.");
            return;
        }
        String svc = serviceId != null ? serviceId.trim() : "";
        if (svc.isBlank() || !target.getCompose().allowsService(svc)) {
            sendLine(router, workflowBotId, channel, "Compose ops aborted: service not allowlisted for this target.");
            return;
        }
        if (truthy(Env.get("DEPLOY_HOST_OPS_DISABLED", "")) || truthy(Env.get("GADGET_HOST_OPS_DISABLED", ""))) {
            sendLine(router, workflowBotId, channel, "Compose ops disabled via env.");
            return;
        }
        POOL.execute(() -> runSync(router, workflowBotId, channel, target, operation, svc));
    }

    private static void runSync(OutboundDeliveryRouter router,
                                String workflowBotId,
                                String progressChannelId,
                                DeployTarget target,
                                ComposeOperation operation,
                                String serviceId) {
        DeployTargetCompose c = target.getCompose();
        sendLine(router, workflowBotId, progressChannelId,
                "**Compose** — target `" + escape(target.getId()) + "`, op `" + operation.name().toLowerCase(Locale.ROOT)
                        + "`, service(s) `" + escape(serviceId) + "`");

        List<String> services = resolveServices(c, serviceId);
        try {
            if (c.getExecutor() == ComposeHostExecutor.CURSOR_AGENT) {
                runViaCursorAgent(router, workflowBotId, progressChannelId, c, operation, services);
            } else {
                runDockerComposeDirect(router, workflowBotId, progressChannelId, c, operation, services);
            }
        } catch (Exception e) {
            log.warn("Compose ops failed: {}", e.getMessage());
            sendLine(router, workflowBotId, progressChannelId, "Compose failed: " + escape(e.getMessage()));
        }
    }

    private static List<String> resolveServices(DeployTargetCompose c, String serviceId) {
        if ("_all".equalsIgnoreCase(serviceId)) {
            return new ArrayList<>(c.allServiceNames());
        }
        return List.of(serviceId);
    }

    private static void runDockerComposeDirect(OutboundDeliveryRouter router,
                                             String workflowBotId,
                                             String channelId,
                                             DeployTargetCompose c,
                                             ComposeOperation operation,
                                             List<String> services) throws Exception {
        List<String> cmd = buildDockerComposeCommand(c, operation, services);
        Path wd = Path.of(c.getWorkingDirectory());
        String prerequisiteFailure = validateDirectComposePrerequisites(wd, c.getFile(), cmd.get(0));
        if (prerequisiteFailure != null) {
            throw new IllegalStateException(prerequisiteFailure);
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(wd.toFile());
        pb.redirectErrorStream(true);
        Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            throw new IOException(buildDirectStartFailureMessage(c.getWorkingDirectory(), cmd.get(0), e), e);
        }
        streamComposeLines(router, workflowBotId, channelId, proc.getInputStream(), maxDiscordLines());
        long timeoutSec = parseLongEnv("DEPLOY_HOST_OPS_TIMEOUT_SECS", 600L);
        if (!proc.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            sendLine(router, workflowBotId, channelId, "Compose timed out after " + timeoutSec + "s.");
            return;
        }
        int code = proc.exitValue();
        if (code == 0) {
            sendLine(router, workflowBotId, channelId, "Compose finished **successfully**.");
        } else {
            sendLine(router, workflowBotId, channelId, "Compose finished with **exit code " + code + "**.");
        }
    }

    private static List<String> buildDockerComposeCommand(DeployTargetCompose c,
                                                          ComposeOperation operation,
                                                          List<String> services) {
        String docker = firstNonBlank(Env.get("DEPLOY_COMPOSE_BINARY", ""), "docker").trim();
        List<String> cmd = new ArrayList<>();
        cmd.add(docker);
        cmd.add("compose");
        cmd.add("-f");
        cmd.add(c.getFile());
        switch (operation) {
            case UP -> {
                cmd.add("up");
                cmd.add("-d");
                cmd.addAll(services);
            }
            case STOP -> {
                cmd.add("stop");
                cmd.addAll(services);
            }
            case RESTART -> {
                cmd.add("restart");
                cmd.addAll(services);
            }
            case PS -> cmd.add("ps");
        }
        return cmd;
    }

    /** Visible for tests. */
    static List<String> buildDockerComposeCommandForTest(DeployTargetCompose c,
                                                         ComposeOperation operation,
                                                         List<String> services) {
        return buildDockerComposeCommand(c, operation, services);
    }

    /** Visible for tests. */
    static String validateDirectComposePrerequisitesForTest(Path workingDirectory, String composeFile, String dockerBinary) {
        return validateDirectComposePrerequisites(workingDirectory, composeFile, dockerBinary);
    }

    private static void runViaCursorAgent(OutboundDeliveryRouter router,
                                          String workflowBotId,
                                          String channelId,
                                          DeployTargetCompose c,
                                          ComposeOperation operation,
                                          List<String> services) throws Exception {
        String inner = shellJoin(buildDockerComposeCommand(c, operation, services));
        String prompt = "In this workspace, run exactly this command non-interactively and report the exit code "
                + "and any errors: " + inner;
        String binary = firstNonBlank(Env.get("DEPLOY_CURSOR_AGENT_BINARY", ""), "cursor").trim();
        List<String> cmd = new ArrayList<>();
        cmd.add(binary);
        cmd.add("agent");
        cmd.add("--print");
        cmd.add("--force");
        cmd.add("--trust");
        cmd.add("--sandbox");
        cmd.add("disabled");
        cmd.add("--workspace");
        cmd.add(c.getWorkingDirectory());
        cmd.add(prompt);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        streamComposeLines(router, workflowBotId, channelId, proc.getInputStream(), maxDiscordLines());
        long timeoutSec = parseLongEnv("DEPLOY_HOST_OPS_TIMEOUT_SECS", 600L);
        if (!proc.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            sendLine(router, workflowBotId, channelId, "Cursor agent timed out after " + timeoutSec + "s.");
            return;
        }
        int code = proc.exitValue();
        sendLine(router, workflowBotId, channelId,
                "Cursor agent finished with **exit code " + code + "**.");
    }

    /** Visible for tests: prompt only. */
    static String buildCursorPromptForTest(DeployTargetCompose c, ComposeOperation operation, List<String> services) {
        String inner = shellJoin(buildDockerComposeCommand(c, operation, services));
        return "In this workspace, run exactly this command non-interactively and report the exit code "
                + "and any errors: " + inner;
    }

    private static String shellJoin(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(escapeShellArg(parts.get(i)));
        }
        return sb.toString();
    }

    private static String escapeShellArg(String s) {
        if (s == null) {
            return "";
        }
        if (s.chars().noneMatch(ch -> Character.isWhitespace(ch) || ch == '"' || ch == '\'')) {
            return s;
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String validateDirectComposePrerequisites(Path workingDirectory, String composeFile, String dockerBinary) {
        if (workingDirectory == null) {
            return "Compose failed: working directory is not configured for this target.";
        }
        if (!Files.exists(workingDirectory) || !Files.isDirectory(workingDirectory)) {
            return "Compose failed: working directory `" + escape(workingDirectory.toString())
                    + "` is not available inside Vinekeepers. Mount the host stack into the container at the same path as `config/deploy-targets.yaml`.";
        }
        Path composePath = workingDirectory.resolve(composeFile).normalize();
        if (!Files.exists(composePath) || Files.isDirectory(composePath)) {
            return "Compose failed: file `" + escape(composePath.toString())
                    + "` is not available inside Vinekeepers. Mount the stack directory at the same path as `config/deploy-targets.yaml`.";
        }
        String cliFailure = validateComposeCli(dockerBinary);
        if (cliFailure != null) {
            return cliFailure;
        }
        if (runningInContainer() && !Files.exists(DOCKER_SOCKET_PATH)) {
            return "Compose failed: Docker socket `" + escape(DOCKER_SOCKET_PATH.toString())
                    + "` is not mounted in the Vinekeepers container. Mount `/var/run/docker.sock:/var/run/docker.sock` to use direct host compose operations.";
        }
        return null;
    }

    private static String validateComposeCli(String dockerBinary) {
        ProcessBuilder pb = new ProcessBuilder(dockerBinary, "compose", "version");
        pb.redirectErrorStream(true);
        try {
            Process proc = pb.start();
            byte[] output = proc.getInputStream().readAllBytes();
            if (!proc.waitFor(15, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return "Compose failed: timed out while checking Docker Compose availability for `" + escape(dockerBinary) + "`.";
            }
            if (proc.exitValue() == 0) {
                return null;
            }
            String rendered = new String(output, StandardCharsets.UTF_8).trim();
            if (rendered.isBlank()) {
                rendered = "docker compose version exited with code " + proc.exitValue();
            }
            return "Compose failed: Docker Compose is not available via `" + escape(dockerBinary)
                    + " compose`. Rebuild the image with the Docker CLI + compose plugin, or set `DEPLOY_COMPOSE_BINARY` to a working binary. Details: "
                    + escape(truncate(rendered, 240));
        } catch (IOException e) {
            return "Compose failed: Docker binary `" + escape(dockerBinary)
                    + "` is not available inside Vinekeepers. Rebuild the image with the Docker CLI + compose plugin, or set `DEPLOY_COMPOSE_BINARY` to a working binary.";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Compose failed: interrupted while checking Docker Compose availability.";
        }
    }

    private static String buildDirectStartFailureMessage(String workingDirectory, String dockerBinary, IOException error) {
        String detail = error.getMessage() != null && !error.getMessage().isBlank()
                ? error.getMessage()
                : error.getClass().getSimpleName();
        return "Compose failed while starting `" + escape(dockerBinary)
                + " compose` in `" + escape(workingDirectory)
                + "`. Ensure the Docker CLI is installed in the Vinekeepers image, `/var/run/docker.sock` is mounted, and the stack path is bind-mounted at the same location inside the container. Details: "
                + escape(detail);
    }

    private static boolean runningInContainer() {
        return Files.exists(Path.of("/.dockerenv"));
    }

    private static void streamComposeLines(OutboundDeliveryRouter router,
                                           String workflowBotId,
                                           String channelId,
                                           InputStream in,
                                           int maxLines) {
        int n = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && n < maxLines) {
                String t = line.trim();
                if (t.isEmpty()) {
                    continue;
                }
                sendLine(router, workflowBotId, channelId, truncate(t, 350));
                n++;
            }
        } catch (Exception e) {
            log.debug("Compose stream ended: {}", e.getMessage());
        }
    }

    private static int maxDiscordLines() {
        long v = parseLongEnv("DEPLOY_HOST_OPS_MAX_DISCORD_LINES", 80L);
        return (int) Math.min(500, Math.max(5, v));
    }

    private static long parseLongEnv(String key, long def) {
        String s = Env.get(key, "").trim();
        if (s.isBlank()) {
            return def;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static void sendLine(OutboundDeliveryRouter router, String workflowBotId, String channelId, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        router.sendAs(channelId, null, text, workflowBotId);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("`", "'");
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }

    private static boolean truthy(String s) {
        if (s == null) return false;
        String t = s.trim().toLowerCase(Locale.ROOT);
        return "1".equals(t) || "true".equals(t) || "yes".equals(t);
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a.trim() : b;
    }
}
