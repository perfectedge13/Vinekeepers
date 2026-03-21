package com.vinekeepers.gadget;

import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.env.Env;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs ansible-playbook (or a dry-run) in the background and posts progress to a Discord channel or thread.
 */
public final class GadgetDeployRunner {

    private static final Logger log = LoggerFactory.getLogger(GadgetDeployRunner.class);

    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "gadget-deploy");
        t.setDaemon(true);
        return t;
    });

    private GadgetDeployRunner() {
    }

    /**
     * Submits deploy work without blocking the caller.
     */
    public static void submit(OutboundDeliveryRouter router,
                              String gadgetBotId,
                              String progressChannelId,
                              GadgetProjectDefinition project,
                              String branch) {
        Objects.requireNonNull(router, "router");
        String botId = gadgetBotId != null ? gadgetBotId : "gadget";
        String channel = progressChannelId != null ? progressChannelId : "";
        if (channel.isBlank()) {
            log.warn("Gadget deploy skipped: no progress channel id");
            return;
        }
        GadgetProjectDefinition p = project;
        if (p == null || !p.isValid()) {
            sendLine(router, botId, channel, "Gadget deploy aborted: invalid project.");
            return;
        }
        String b = branch != null ? branch.trim() : "";
        final String resolvedBranch = b.isBlank() ? "main" : b;
        POOL.execute(() -> runSync(router, botId, channel, p, resolvedBranch));
    }

    private static void runSync(OutboundDeliveryRouter router,
                                String gadgetBotId,
                                String progressChannelId,
                                GadgetProjectDefinition project,
                                String branch) {
        sendLine(router, gadgetBotId, progressChannelId,
                "**Gadget deploy** — project `" + escape(project.getId()) + "`, branch `" + escape(branch) + "`");

        if (truthy(Env.get("GADGET_ANSIBLE_DISABLED", ""))) {
            sendLine(router, gadgetBotId, progressChannelId,
                    "Dry run: `GADGET_ANSIBLE_DISABLED` is set; skipping ansible-playbook.");
            return;
        }

        Path root = ansibleRoot();
        Path playbook = root.resolve(project.getPlaybook()).normalize();
        if (!playbook.startsWith(root) || !Files.isRegularFile(playbook)) {
            sendLine(router, gadgetBotId, progressChannelId,
                    "Deploy failed: playbook not found at `" + escape(playbook.toString()) + "` (root `" + root + "`).");
            return;
        }

        List<String> cmd = buildCommand(playbook, project, branch);
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(root.toFile());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            streamImportantLines(router, gadgetBotId, progressChannelId, proc.getInputStream());
            int code = proc.waitFor();
            if (code == 0) {
                sendLine(router, gadgetBotId, progressChannelId, "Deploy finished **successfully**.");
            } else {
                sendLine(router, gadgetBotId, progressChannelId, "Deploy finished with **exit code " + code + "**.");
            }
        } catch (Exception e) {
            log.warn("Gadget deploy failed: {}", e.getMessage());
            sendLine(router, gadgetBotId, progressChannelId, "Deploy failed: " + escape(e.getMessage()));
        }
    }

    private static List<String> buildCommand(Path playbook, GadgetProjectDefinition project, String branch) {
        String binary = firstNonBlank(Env.get("DEPLOY_ANSIBLE_BINARY", ""),
                firstNonBlank(Env.get("GADGET_ANSIBLE_BINARY", ""), "ansible-playbook"));
        List<String> cmd = new ArrayList<>();
        cmd.add(binary);
        String inventory = firstNonBlank(Env.get("DEPLOY_ANSIBLE_INVENTORY", ""),
                Env.get("GADGET_ANSIBLE_INVENTORY", "")).trim();
        if (!inventory.isBlank()) {
            cmd.add("-i");
            cmd.add(inventory);
        }
        cmd.add(playbook.toString());
        String projectId = project.getId();
        appendExtraVar(cmd, "project_id", projectId);
        appendExtraVar(cmd, "branch", branch);
        appendExtraVar(cmd, "gadget_project", projectId);
        appendExtraVar(cmd, "gadget_branch", branch);
        for (Map.Entry<String, String> e : project.getExtraVars().entrySet()) {
            appendExtraVar(cmd, e.getKey(), e.getValue());
        }
        return cmd;
    }

    private static void appendExtraVar(List<String> cmd, String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }
        String v = value != null ? value : "";
        cmd.add("-e");
        cmd.add(key + "=" + escapeExtraVarValue(v));
    }

    /** Single-line scalar for {@code -e key=value} (no shell). Visible for tests. */
    public static String escapeExtraVarValue(String v) {
        String t = v.replace("\r", " ").replace("\n", " ").trim();
        if (t.contains("\\") || t.contains("\"")) {
            return "\"" + t.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return t;
    }

    private static void streamImportantLines(OutboundDeliveryRouter router,
                                             String gadgetBotId,
                                             String channelId,
                                             InputStream in) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String t = line.trim();
                if (t.isEmpty()) {
                    continue;
                }
                if (shouldEmitLine(t)) {
                    sendLine(router, gadgetBotId, channelId, truncate(t, 350));
                }
            }
        } catch (Exception e) {
            log.debug("Gadget deploy stream read ended: {}", e.getMessage());
        }
    }

    private static boolean shouldEmitLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return line.startsWith("TASK [")
                || line.startsWith("PLAY [")
                || lower.contains("fatal:")
                || lower.contains("failed=1")
                || lower.startsWith("changed:")
                || lower.startsWith("ok:");
    }

    private static Path ansibleRoot() {
        String raw = Env.get("GADGET_ANSIBLE_ROOT", "").trim();
        if (!raw.isBlank()) {
            return Path.of(raw);
        }
        return Path.of("ansible");
    }

    private static void sendLine(OutboundDeliveryRouter router, String gadgetBotId, String channelId, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        router.sendAs(channelId, null, text, gadgetBotId);
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("`", "'");
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

    /** Visible for tests: detect playbook path resolution without executing ansible. */
    static Path resolvePlaybookForTest(GadgetProjectDefinition project) {
        Path root = ansibleRoot();
        return root.resolve(project.getPlaybook()).normalize();
    }
}
