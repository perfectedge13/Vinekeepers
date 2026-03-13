package com.vinekeepers.core;

import com.vinekeepers.audit.AuditRecorder;
import com.vinekeepers.bot.BotDefinition;
import com.vinekeepers.bot.Router;
import com.vinekeepers.config.BotConfig;
import com.vinekeepers.config.ConfigLoader;
import com.vinekeepers.connectors.DiscordAppReplySink;
import com.vinekeepers.connectors.DiscordEventSource;
import com.vinekeepers.connectors.GitHubEventSource;
import com.vinekeepers.connectors.JdaDiscordGateway;
import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.core.cursor.CursorCloudAdapter;
import com.vinekeepers.core.cursor.CursorCloudAdapterImpl;
import com.vinekeepers.core.cursor.CursorCloudRunMonitor;
import com.vinekeepers.env.Env;
import com.vinekeepers.env.HealthServer;
import com.vinekeepers.events.EventBus;
import com.vinekeepers.reasoner.StubReasoner;
import com.vinekeepers.state.LifecycleContextStore;
import com.vinekeepers.state.StateStore;
import com.vinekeepers.tools.CursorFullRunTool;
import com.vinekeepers.tools.EchoTool;
import com.vinekeepers.tools.ToolRegistry;
import com.vinekeepers.tools.ToolRunner;
import com.vinekeepers.providers.GitHubReposChoiceProvider;
import com.vinekeepers.workflow.DynamicChoiceProviderRegistry;
import com.vinekeepers.workflow.WorkflowActionRegistry;
import com.vinekeepers.workflow.WorkflowRunner;
import com.vinekeepers.workflow.WorkflowRunnerFactory;
import com.vinekeepers.workflow.actions.CreateChannelAction;
import com.vinekeepers.workflow.actions.CreateLifecycleContextAction;
import com.vinekeepers.workflow.actions.CreateThreadAction;
import com.vinekeepers.workflow.actions.LaunchCursorRunAction;
import com.vinekeepers.workflow.actions.PostChannelMessageAction;
import com.vinekeepers.workflow.actions.ProvisionBotInstanceAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wires EventBus, Engine, Router, connectors, and optional YAML config.
 */
public final class Bootstrap {

    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);

    private final EventBus eventBus;
    private final VinekeepersEngine engine;
    private final Router router;
    private final StateStore stateStore;
    private final LifecycleContextStore lifecycleContextStore;
    private final CursorCloudAdapter cursorCloudAdapter;
    private final CursorCloudRunMonitor cursorCloudRunMonitor;
    private final ToolRegistry toolRegistry;
    private final ToolRunner toolRunner;
    private final WorkflowActionRegistry actionRegistry;
    private final OutboundDeliveryRouter outboundDeliveryRouter;
    private final List<BotDefinition> lastLoadedBots = new ArrayList<>();
    private final Set<String> routedBotIds = new HashSet<>();
    /** From config defaultDiscordTokenEnvKey; used only when no bot has discordTokenEnvKey. Bot-config driven. */
    private String defaultDiscordTokenEnvKey;
    private final List<DiscordEventSource> discordSources = new ArrayList<>();
    private DiscordEventSource discordSource;
    private GitHubEventSource githubSource;
    private HealthServer healthServer;

    public Bootstrap() {
        this.eventBus = new EventBus();
        this.stateStore = new StateStore();
        this.lifecycleContextStore = new LifecycleContextStore();
        this.cursorCloudAdapter = new CursorCloudAdapterImpl();
        this.cursorCloudRunMonitor = new CursorCloudRunMonitor(
                cursorCloudAdapter,
                stateStore,
                parseLong(Env.get("CURSOR_POLL_INTERVAL_MS", "15000"), 15000L));
        this.toolRegistry = new ToolRegistry();
        this.toolRunner = new ToolRunner(toolRegistry);
        this.actionRegistry = new WorkflowActionRegistry();
        this.outboundDeliveryRouter = new OutboundDeliveryRouter(lifecycleContextStore);
        registerLegacyActions(actionRegistry);
        registerLifecycleActions(actionRegistry);
        AuditRecorder audit = entry -> log.info("Audit: {} {} {} {}", entry.getTimestamp(), entry.getBotId(), entry.getAction(), entry.getDetail());
        this.router = new Router(lifecycleContextStore);
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
            defaultDiscordTokenEnvKey = config.getDefaultDiscordTokenEnvKey();
            if (defaultDiscordTokenEnvKey != null && defaultDiscordTokenEnvKey.isBlank()) {
                defaultDiscordTokenEnvKey = null;
            }
            router.clear();
            routedBotIds.clear();
            if (config.getRouting() != null) {
                for (Map<String, Object> r : config.getRouting()) {
                    Object botId = r != null ? r.get("botId") : null;
                    if (botId != null && !botId.toString().isBlank()) {
                        routedBotIds.add(botId.toString().trim());
                    }
                }
            }
            loader.addRoutings(config, router);
            DynamicChoiceProviderRegistry choiceProviderRegistry = new DynamicChoiceProviderRegistry();
            choiceProviderRegistry.register("githubRepos", new GitHubReposChoiceProvider(stateStore));
            List<BotDefinition> bots = loader.buildBots(config);
            lastLoadedBots.clear();
            lastLoadedBots.addAll(bots);
            Map<String, Boolean> handlesMap = new HashMap<>();
            for (BotDefinition bot : bots) {
                handlesMap.put(bot.getId(), bot.isHandlesOwnedSpaces());
            }
            router.setHandlesOwnedSpacesByBotId(handlesMap);
            for (BotDefinition bot : bots) {
                engine.registerBot(bot);
                WorkflowRunner runner = WorkflowRunnerFactory.create(bot, config.getWorkflows(), this.actionRegistry, toolRunner, choiceProviderRegistry);
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
        boolean anyWithToken = lastLoadedBots.stream()
                .anyMatch(b -> b.getDiscordTokenEnvKey() != null && !b.getDiscordTokenEnvKey().isBlank());
        if (anyWithToken) {
            for (BotDefinition bot : lastLoadedBots) {
                String envKey = bot.getDiscordTokenEnvKey();
                if (envKey == null || envKey.isBlank()) {
                    continue;
                }
                String token = Env.get(envKey, "");
                if (token.isBlank()) {
                    log.warn("Bot {} has discordTokenEnvKey {} but token is blank; skipping Discord connector for this bot.", bot.getId(), envKey);
                    continue;
                }
                boolean outboundOnly = !routedBotIds.contains(bot.getId());
                JdaDiscordGateway gateway = new JdaDiscordGateway(token, outboundOnly);
                DiscordEventSource source = new DiscordEventSource(gateway);
                outboundDeliveryRouter.registerSender(bot.getId(), source, gateway);
                if (outboundDeliveryRouter.getDefaultGateway() == null) {
                    outboundDeliveryRouter.setDefaultSender(source);
                    outboundDeliveryRouter.setDefaultGateway(gateway);
                }
                source.start(eventBus);
                discordSources.add(source);
            }
        }
        if (outboundDeliveryRouter.getDefaultGateway() == null && defaultDiscordTokenEnvKey != null && !defaultDiscordTokenEnvKey.isBlank()) {
            String token = Env.get(defaultDiscordTokenEnvKey, "");
            if (!token.isBlank()) {
                JdaDiscordGateway gateway = new JdaDiscordGateway(token);
                this.discordSource = new DiscordEventSource(gateway);
                outboundDeliveryRouter.setDefaultSender(discordSource);
                outboundDeliveryRouter.setDefaultGateway(gateway);
                for (BotDefinition bot : lastLoadedBots) {
                    outboundDeliveryRouter.registerSender(bot.getId(), discordSource, gateway);
                }
                discordSource.start(eventBus);
                discordSources.add(discordSource);
            }
        }
        engine.setReplySender(outboundDeliveryRouter);
        engine.registerSink("discord", new DiscordAppReplySink(outboundDeliveryRouter));
        cursorCloudRunMonitor.setReplySender(outboundDeliveryRouter);
        if (outboundDeliveryRouter.getDefaultGateway() != null) {
            actionRegistry.register("create_channel", new CreateChannelAction(outboundDeliveryRouter));
            actionRegistry.register("create_thread", new CreateThreadAction(outboundDeliveryRouter, lifecycleContextStore));
        }
        actionRegistry.register("post_channel_message", new PostChannelMessageAction(outboundDeliveryRouter));
        return this;
    }

    public Bootstrap withGitHub() {
        this.githubSource = new GitHubEventSource();
        githubSource.start(eventBus);
        return this;
    }

    /**
     * Start the health HTTP server for Prometheus/Grafana probes (optional).
     * Port from HEALTH_PORT env, default 8080. No-op if port is &lt;= 0 or bind fails.
     */
    public Bootstrap withHealthServer() {
        int port = parseInt(Env.get("HEALTH_PORT", "8080"), 8080);
        if (port <= 0) {
            return this;
        }
        try {
            this.healthServer = new HealthServer(port);
            healthServer.start();
        } catch (Exception e) {
            log.warn("Health server not started on port {}: {}", port, e.getMessage());
        }
        return this;
    }

    private static void registerLegacyActions(WorkflowActionRegistry registry) {
        registry.register("cursor_cloud", (event, state, bind) -> "Cursor Cloud action (stub)");
    }

    private void registerLifecycleActions(WorkflowActionRegistry registry) {
        registry.register("provision_bot_instance", new ProvisionBotInstanceAction(stateStore));
        registry.register("create_lifecycle_context", new CreateLifecycleContextAction(lifecycleContextStore));
        registry.register("launch_cursor_run", new LaunchCursorRunAction(cursorCloudAdapter, stateStore, lifecycleContextStore));
    }

    private void registerTools(ToolRegistry registry) {
        registry.register(new CursorFullRunTool(cursorCloudAdapter, stateStore));
        registry.register(new EchoTool());
    }

    public void shutdown() {
        if (healthServer != null) healthServer.stop();
        for (DiscordEventSource source : discordSources) {
            if (source != null) source.stop();
        }
        discordSources.clear();
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

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
