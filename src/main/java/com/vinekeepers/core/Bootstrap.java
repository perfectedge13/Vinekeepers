package com.vinekeepers.core;

import com.vinekeepers.audit.AuditRecorder;
import com.vinekeepers.bot.BotDefinition;
import com.vinekeepers.bot.Router;
import com.vinekeepers.config.BotConfig;
import com.vinekeepers.config.ConfigLoader;
import com.vinekeepers.connectors.DiscordEventSource;
import com.vinekeepers.connectors.GitHubEventSource;
import com.vinekeepers.core.cursor.CursorCloudAdapterImpl;
import com.vinekeepers.events.Event;
import com.vinekeepers.events.EventBus;
import com.vinekeepers.reasoner.StubReasoner;
import com.vinekeepers.state.StateStore;
import com.vinekeepers.workflow.WorkflowActionRegistry;
import com.vinekeepers.workflow.WorkflowRunner;
import com.vinekeepers.workflow.WorkflowRunnerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Wires EventBus, Engine, Router, connectors, and optional YAML config.
 */
public final class Bootstrap {

    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);

    private final EventBus eventBus;
    private final VinekeepersEngine engine;
    private final Router router;
    private final StateStore stateStore;
    private DiscordEventSource discordSource;
    private GitHubEventSource githubSource;

    public Bootstrap() {
        this.eventBus = new EventBus();
        this.stateStore = new StateStore();
        AuditRecorder audit = entry -> log.info("Audit: {} {} {} {}", entry.getTimestamp(), entry.getBotId(), entry.getAction(), entry.getDetail());
        this.router = new Router();
        this.engine = new VinekeepersEngine(router, stateStore, audit);
        eventBus.subscribe(engine);
    }

    /**
     * Load bots and routing from YAML if path is present.
     */
    public Bootstrap loadConfig(Path configPath) {
        if (configPath == null || !configPath.toFile().exists()) {
            return this;
        }
        try {
            ConfigLoader loader = new ConfigLoader();
            BotConfig config = loader.loadFromPath(configPath);
            router.clear();
            loader.addRoutings(config, router);
            WorkflowActionRegistry actionRegistry = new WorkflowActionRegistry();
            registerCursorActions(actionRegistry);
            List<BotDefinition> bots = loader.buildBots(config);
            for (BotDefinition bot : bots) {
                engine.registerBot(bot);
                WorkflowRunner runner = WorkflowRunnerFactory.create(
                        bot.getWorkflowType(), bot.getWorkflowParams(),
                        config.getWorkflows(), actionRegistry);
                engine.registerRunner(bot.getId(), runner);
                engine.registerReasoner(bot.getId(), new StubReasoner());
            }
        } catch (Exception e) {
            log.warn("Could not load config from {}: {}", configPath, e.getMessage());
        }
        return this;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public VinekeepersEngine getEngine() {
        return engine;
    }

    public Router getRouter() {
        return router;
    }

    public Bootstrap withDiscord() {
        this.discordSource = new DiscordEventSource();
        engine.setReplySender(discordSource);
        discordSource.start(eventBus);
        return this;
    }

    public Bootstrap withGitHub() {
        this.githubSource = new GitHubEventSource();
        githubSource.start(eventBus);
        return this;
    }

    /**
     * Register Cursor-related actions for use by configured workflows (CallActionStep).
     */
    private static void registerCursorActions(WorkflowActionRegistry registry) {
        CursorCloudAdapterImpl adapter = new CursorCloudAdapterImpl();
        registry.register("cursor.fullRun", (Event event, Map<String, Object> state, Map<String, Object> bind) -> {
            String project = getString(state, "project");
            String change = getString(state, "codeChange");
            if (change == null || change.isBlank()) change = getString(state, "changeDescription");
            if (project == null || project.isBlank()) return "Missing project in state.";
            if (change == null || change.isBlank()) change = "Luna change";
            String branch = "luna-feature";
            String title = "Luna: " + (change.length() > 60 ? change.substring(0, 57) + "..." : change);
            StringBuilder out = new StringBuilder();
            out.append(adapter.createBranch(project, branch)).append("; ");
            out.append(adapter.runNovaCommit(project, change)).append("; ");
            out.append(adapter.push(project)).append("; ");
            out.append(adapter.createPr(project, title));
            return out.toString();
        });
        registry.register("cursor_cloud", (event, state, bind) -> "Cursor Cloud action (stub)");
        registry.register("echo", (event, state, bind) -> bind != null ? bind.get("message") : null);
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    public void shutdown() {
        if (discordSource != null) discordSource.stop();
        if (githubSource != null) githubSource.stop();
    }
}
