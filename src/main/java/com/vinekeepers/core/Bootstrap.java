package com.vinekeepers.core;

import com.vinekeepers.audit.AuditRecorder;
import com.vinekeepers.bot.BotDefinition;
import com.vinekeepers.bot.Router;
import com.vinekeepers.config.BotConfig;
import com.vinekeepers.config.ConfigLoader;
import com.vinekeepers.connectors.DiscordEventSource;
import com.vinekeepers.connectors.GitHubEventSource;
import com.vinekeepers.core.cursor.CursorCloudAdapter;
import com.vinekeepers.core.cursor.CursorCloudAdapterImpl;
import com.vinekeepers.core.cursor.CursorCloudRunMonitor;
import com.vinekeepers.env.Env;
import com.vinekeepers.events.EventBus;
import com.vinekeepers.reasoner.StubReasoner;
import com.vinekeepers.state.StateStore;
import com.vinekeepers.tools.CursorFullRunTool;
import com.vinekeepers.tools.EchoTool;
import com.vinekeepers.tools.ToolRegistry;
import com.vinekeepers.tools.ToolRunner;
import com.vinekeepers.workflow.WorkflowActionRegistry;
import com.vinekeepers.workflow.WorkflowRunner;
import com.vinekeepers.workflow.WorkflowRunnerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * Wires EventBus, Engine, Router, connectors, and optional YAML config.
 */
public final class Bootstrap {

    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);

    private final EventBus eventBus;
    private final VinekeepersEngine engine;
    private final Router router;
    private final StateStore stateStore;
    private final CursorCloudAdapter cursorCloudAdapter;
    private final CursorCloudRunMonitor cursorCloudRunMonitor;
    private final ToolRegistry toolRegistry;
    private final ToolRunner toolRunner;
    private DiscordEventSource discordSource;
    private GitHubEventSource githubSource;

    public Bootstrap() {
        this.eventBus = new EventBus();
        this.stateStore = new StateStore();
        this.cursorCloudAdapter = new CursorCloudAdapterImpl();
        this.cursorCloudRunMonitor = new CursorCloudRunMonitor(
                cursorCloudAdapter,
                stateStore,
                parseLong(Env.get("CURSOR_POLL_INTERVAL_MS", "15000"), 15000L));
        this.toolRegistry = new ToolRegistry();
        this.toolRunner = new ToolRunner(toolRegistry);
        AuditRecorder audit = entry -> log.info("Audit: {} {} {} {}", entry.getTimestamp(), entry.getBotId(), entry.getAction(), entry.getDetail());
        this.router = new Router();
        this.engine = new VinekeepersEngine(router, stateStore, audit, toolRunner);
        eventBus.subscribe(engine);
        registerTools(toolRegistry);
        cursorCloudRunMonitor.start();
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
            WorkflowActionRegistry actionRegistry = createActionRegistry();
            List<BotDefinition> bots = loader.buildBots(config);
            for (BotDefinition bot : bots) {
                engine.registerBot(bot);
                WorkflowRunner runner = WorkflowRunnerFactory.create(bot, config.getWorkflows(), actionRegistry, toolRunner);
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
        cursorCloudRunMonitor.setReplySender(discordSource);
        return this;
    }

    public Bootstrap withGitHub() {
        this.githubSource = new GitHubEventSource();
        githubSource.start(eventBus);
        return this;
    }

    private static void registerLegacyActions(WorkflowActionRegistry registry) {
        registry.register("cursor_cloud", (event, state, bind) -> "Cursor Cloud action (stub)");
    }

    private void registerTools(ToolRegistry registry) {
        registry.register(new CursorFullRunTool(cursorCloudAdapter, stateStore));
        registry.register(new EchoTool());
    }

    private static WorkflowActionRegistry createActionRegistry() {
        WorkflowActionRegistry registry = new WorkflowActionRegistry();
        registerLegacyActions(registry);
        return registry;
    }

    public void shutdown() {
        if (discordSource != null) discordSource.stop();
        if (githubSource != null) githubSource.stop();
        cursorCloudRunMonitor.close();
    }

    private static long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
