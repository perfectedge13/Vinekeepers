package com.vinekeepers.devops;

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
 * Runs ansible-playbook asynchronously and posts condensed output to Discord via {@link OutboundDeliveryRouter#sendAs}.
 */
public final class AnsiblePlaybookDeployRunner {

    private static final Logger log = LoggerFactory.getLogger(AnsiblePlaybookDeployRunner.class);

    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ansible-playbook-deploy");
        t.setDaemon(true);
        return t;
    });

    private AnsiblePlaybookDeployRunner() {
    }

    /**
     * @param workflowBotId required (e.g. {@code __botId} from workflow state); must not be null/blank
     */
    public static void submit(OutboundDeliveryRouter router,
                              String workflowBotId,
                              String progressChannelId,
                              DeployTarget target,
                              String branch) {
        Objects.requireNonNull(router, "router");
        if (workflowBotId == null || workflowBotId.isBlank()) {
            log.warn("Ansible deploy skipped: missing workflow bot id (__botId)");
            return;
        }
        String channel = progressChannelId != null ? progressChannelId : "";
        if (channel.isBlank()) {
            log.warn("Ansible deploy skipped: no progress channel id");
            return;
        }
        DeployTarget t = target;
        if (t == null || !t.isValid() || !t.hasAnsiblePlaybook()) {
            sendLine(router, workflowBotId, channel, "Ansible deploy aborted: invalid target or missing playbook.");
            return;
        }
        String b = branch != null ? branch.trim() : "";
        final String resolvedBranch = b.isBlank() ? "main" : b;
        POOL.execute(() -> runSync(router, workflowBotId, channel, t, resolvedBranch));
    }

    private static void runSync(OutboundDeliveryRouter router,
                                String workflowBotId,
                                String progressChannelId,
                                DeployTarget target,
                                String branch) {
        sendLine(router, workflowBotId, progressChannelId,
                "**Ansible deploy** — target `" + escape(target.getId()) + "`, branch `" + escape(branch) + "`");

        if (truthy(Env.get("DEPLOY_ANSIBLE_DISABLED", "")) || truthy(Env.get("GADGET_ANSIBLE_DISABLED", ""))) {
            sendLine(router, workflowBotId, progressChannelId,
                    "Dry run: Ansible disabled via env; skipping ansible-playbook.");
            return;
        }

        Path root = ansibleRoot();
        Path playbook = root.resolve(target.getPlaybook()).normalize();
        if (!playbook.startsWith(root) || !Files.isRegularFile(playbook)) {
            sendLine(router, workflowBotId, progressChannelId,
                    "Deploy failed: playbook not found at `" + escape(playbook.toString()) + "` (root `" + root + "`).");
            return;
        }

        List<String> cmd = buildCommand(playbook, target, branch);
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(root.toFile());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            streamImportantLines(router, workflowBotId, progressChannelId, proc.getInputStream());
            int code = proc.waitFor();
            if (code == 0) {
                sendLine(router, workflowBotId, progressChannelId, "Deploy finished **successfully**.");
            } else {
                sendLine(router, workflowBotId, progressChannelId, "Deploy finished with **exit code " + code + "**.");
            }
        } catch (Exception e) {
            log.warn("Ansible deploy failed: {}", e.getMessage());
            sendLine(router, workflowBotId, progressChannelId, "Deploy failed: " + escape(e.getMessage()));
        }
    }

    private static List<String> buildCommand(Path playbook, DeployTarget target, String branch) {
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
        String tid = target.getId();
        appendExtraVar(cmd, "project_id", tid);
        appendExtraVar(cmd, "branch", branch);
        appendExtraVar(cmd, "deploy_target", tid);
        appendExtraVar(cmd, "deploy_project", tid);
        appendExtraVar(cmd, "gadget_project", tid);
        appendExtraVar(cmd, "gadget_branch", branch);
        for (Map.Entry<String, String> e : target.getExtraVars().entrySet()) {
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
                                             String workflowBotId,
                                             String channelId,
                                             InputStream in) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String t = line.trim();
                if (t.isEmpty()) {
                    continue;
                }
                if (shouldEmitAnsibleLine(t)) {
                    sendLine(router, workflowBotId, channelId, truncate(t, 350));
                }
            }
        } catch (Exception e) {
            log.debug("Ansible deploy stream read ended: {}", e.getMessage());
        }
    }

    private static boolean shouldEmitAnsibleLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return line.startsWith("TASK [")
                || line.startsWith("PLAY [")
                || lower.contains("fatal:")
                || lower.contains("failed=1")
                || lower.startsWith("changed:")
                || lower.startsWith("ok:");
    }

    private static Path ansibleRoot() {
        String raw = firstNonBlank(Env.get("DEPLOY_ANSIBLE_ROOT", ""), Env.get("GADGET_ANSIBLE_ROOT", "")).trim();
        if (!raw.isBlank()) {
            return Path.of(raw);
        }
        return Path.of("ansible");
    }

    private static void sendLine(OutboundDeliveryRouter router, String workflowBotId, String channelId, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        router.sendAs(channelId, null, text, workflowBotId);
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

    /** Visible for tests: playbook path resolution without executing ansible. */
    static Path resolvePlaybookForTest(DeployTarget target) {
        Path root = ansibleRoot();
        return root.resolve(target.getPlaybook()).normalize();
    }
}
