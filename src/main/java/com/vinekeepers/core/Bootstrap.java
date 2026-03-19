package com.vinekeepers.core;

import com.vinekeepers.audit.AuditRecorder;
import com.vinekeepers.bot.BotDefinition;
import com.vinekeepers.bot.Router;
import com.vinekeepers.config.BotConfig;
import com.vinekeepers.config.ConfigLoader;
import com.vinekeepers.connectors.ConnectorContext;
import com.vinekeepers.connectors.ConnectorRegistry;
import com.vinekeepers.connectors.DiscordAppReplySink;
import com.vinekeepers.connectors.DiscordConnectorAdapter;
import com.vinekeepers.connectors.DiscordConnectorConfig;
import com.vinekeepers.connectors.DiscordEventSource;
import com.vinekeepers.connectors.DiscordReplyTargetResolver;
import com.vinekeepers.connectors.GitHubEventSource;
import com.vinekeepers.connectors.DiscordSpaceOperations;
import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.connectors.SpaceOperationsRegistry;
import com.vinekeepers.core.cursor.CursorCloudAdapter;
import com.vinekeepers.core.cursor.CursorCloudAdapterImpl;
import com.vinekeepers.core.cursor.CursorCloudRunMonitor;
import com.vinekeepers.env.Env;
import com.vinekeepers.env.HealthServer;
import com.vinekeepers.events.EventBus;
import com.vinekeepers.reasoner.StubReasoner;
import com.vinekeepers.state.LifecycleContextStore;
import com.vinekeepers.state.StateStore;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.repo.DefaultRepoRefResolver;
import com.vinekeepers.state.repo.RepoWorkspaceService;
import com.vinekeepers.state.repo.RepoWorkspaceStateStore;
import com.vinekeepers.tools.CursorFullRunTool;
import com.vinekeepers.tools.EchoTool;
import com.vinekeepers.tools.ToolRegistry;
import com.vinekeepers.tools.ToolRunner;
import com.vinekeepers.profile.WorkProfileLoader;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.providers.GitHubReposChoiceProvider;
import com.vinekeepers.workflow.DynamicChoiceProviderRegistry;
import com.vinekeepers.workflow.WorkflowActionRegistry;
import com.vinekeepers.workflow.WorkflowRunner;
import com.vinekeepers.workflow.WorkflowRunnerFactory;
import com.vinekeepers.workflow.actions.AppendAssumptionAction;
import com.vinekeepers.workflow.actions.AppendIssueAction;
import com.vinekeepers.workflow.actions.AppendRequirementAction;
import com.vinekeepers.workflow.actions.AppendValidationNoteAction;
import com.vinekeepers.workflow.actions.BuildDiscoveryAgendaAction;
import com.vinekeepers.workflow.actions.CaptureAndApplyDiscoveryAnswerAction;
import com.vinekeepers.workflow.actions.ClassifyAssumptionOrIssueAction;
import com.vinekeepers.workflow.actions.CreateChannelAction;
import com.vinekeepers.workflow.actions.CreateLifecycleContextAction;
import com.vinekeepers.workflow.actions.CreateThreadAction;
import com.vinekeepers.workflow.actions.EnsureRepoWorkspaceAction;
import com.vinekeepers.workflow.actions.GetProfileMissingFieldsAction;
import com.vinekeepers.workflow.actions.GetStructuredDiscoveryGapsAction;
import com.vinekeepers.workflow.actions.InitializeFeaturePlanStateAction;
import com.vinekeepers.workflow.actions.InitializeFeatureRoomStateAction;
import com.vinekeepers.workflow.actions.LaunchCursorRunAction;
import com.vinekeepers.workflow.actions.PostChannelMessageAction;
import com.vinekeepers.workflow.actions.ProvisionBotInstanceAction;
import com.vinekeepers.workflow.actions.ProvisionRoomParticipantsAction;
import com.vinekeepers.workflow.actions.RecomputePlanProgressAction;
import com.vinekeepers.workflow.actions.SetPlanSectionStatusAction;
import com.vinekeepers.workflow.actions.SetSolutionOutlineAction;
import com.vinekeepers.workflow.actions.UpsertArtifactSectionDataAction;
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
    private final FeatureRoomStateStore featureRoomStateStore;
    private final FeaturePlanStateStore featurePlanStateStore;
    private final RepoWorkspaceStateStore repoWorkspaceStateStore;
    private final RepoWorkspaceService repoWorkspaceService;
    private final WorkProfileRegistry workProfileRegistry;
    private final CursorCloudAdapter cursorCloudAdapter;
    private final CursorCloudRunMonitor cursorCloudRunMonitor;
    private final ToolRegistry toolRegistry;
    private final ToolRunner toolRunner;
    private final WorkflowActionRegistry actionRegistry;
    private final OutboundDeliveryRouter outboundDeliveryRouter;
    private final SpaceOperationsRegistry spaceOperationsRegistry = new SpaceOperationsRegistry();
    private final ConnectorRegistry connectorRegistry = new ConnectorRegistry();
    private final List<BotDefinition> lastLoadedBots = new ArrayList<>();
    private final Set<String> routedBotIds = new HashSet<>();
    /** From config defaultDiscordTokenEnvKey (retained transitional root-level); used when no bot has identities.discord.tokenEnvKey. */
    private String defaultDiscordTokenEnvKey;
    private GitHubEventSource githubSource;
    private HealthServer healthServer;

    public Bootstrap() {
        this.eventBus = new EventBus();
        this.stateStore = new StateStore();
        this.lifecycleContextStore = new LifecycleContextStore();
        this.featureRoomStateStore = new FeatureRoomStateStore();
        this.featurePlanStateStore = new FeaturePlanStateStore();
        this.repoWorkspaceStateStore = new RepoWorkspaceStateStore();
        this.repoWorkspaceService = RepoWorkspaceService.fromEnv(new DefaultRepoRefResolver());
        this.workProfileRegistry = WorkProfileLoader.load(Path.of("config", "work-profiles.yaml"));
        this.cursorCloudAdapter = new CursorCloudAdapterImpl();
        this.cursorCloudRunMonitor = new CursorCloudRunMonitor(
                cursorCloudAdapter,
                stateStore,
                parseLong(Env.get("CURSOR_POLL_INTERVAL_MS", "15000"), 15000L));
        this.toolRegistry = new ToolRegistry();
        this.toolRunner = new ToolRunner(toolRegistry);
        this.actionRegistry = new WorkflowActionRegistry();
        this.outboundDeliveryRouter = new OutboundDeliveryRouter(lifecycleContextStore, featureRoomStateStore);
        registerLegacyActions(actionRegistry);
        registerLifecycleActions(actionRegistry);
        AuditRecorder audit = entry -> log.info("Audit: {} {} {} {}", entry.getTimestamp(), entry.getBotId(), entry.getAction(), entry.getDetail());
        this.router = new Router(lifecycleContextStore, featureRoomStateStore);
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
                boolean handles = bot.getConnectorIdentity("discord")
                        .map(d -> d.getBoolean("handlesOwnedSpaces"))
                        .orElse(false);
                handlesMap.put(bot.getId(), handles);
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
        DiscordConnectorConfig discordConfig = new DiscordConnectorConfig(defaultDiscordTokenEnvKey);
        DiscordConnectorAdapter discordAdapter = new DiscordConnectorAdapter(discordConfig, routedBotIds);
        connectorRegistry.register("discord", discordAdapter);
        ConnectorContext context = new ConnectorContext(eventBus, outboundDeliveryRouter);
        discordAdapter.registerBots(lastLoadedBots, context);

        engine.setReplySender("discord", outboundDeliveryRouter);
        engine.registerSink("discord", new DiscordAppReplySink(outboundDeliveryRouter));
        engine.registerReplyTargetResolver("discord", new DiscordReplyTargetResolver());
        cursorCloudRunMonitor.setReplySender(outboundDeliveryRouter);
        spaceOperationsRegistry.register("discord", new DiscordSpaceOperations(outboundDeliveryRouter, lifecycleContextStore));
        // Discord gateway wired: register create_channel/create_thread only when a default gateway is available
        if (outboundDeliveryRouter.getDefaultGateway() != null) {
            actionRegistry.register("create_channel", new CreateChannelAction(spaceOperationsRegistry));
            actionRegistry.register("create_thread", new CreateThreadAction(spaceOperationsRegistry));
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
        registry.register("provision_room_participants", new ProvisionRoomParticipantsAction(stateStore));
        registry.register("initialize_feature_room_state", new InitializeFeatureRoomStateAction(featureRoomStateStore));
        registry.register("initialize_feature_plan_state", new InitializeFeaturePlanStateAction(
                featurePlanStateStore, featureRoomStateStore, workProfileRegistry));
        registry.register("upsert_artifact_section_data", new UpsertArtifactSectionDataAction(featurePlanStateStore, workProfileRegistry));
        registry.register("get_profile_missing_fields", new GetProfileMissingFieldsAction(featurePlanStateStore, workProfileRegistry));
        registry.register("get_structured_discovery_gaps", new GetStructuredDiscoveryGapsAction(featurePlanStateStore, workProfileRegistry));
        registry.register("build_discovery_agenda", new BuildDiscoveryAgendaAction());
        registry.register("capture_and_apply_discovery_answer", new CaptureAndApplyDiscoveryAnswerAction(featurePlanStateStore, workProfileRegistry));
        registry.register("classify_assumption_or_issue", new ClassifyAssumptionOrIssueAction(featurePlanStateStore));
        registry.register("recompute_plan_progress", new RecomputePlanProgressAction(featurePlanStateStore, workProfileRegistry));
        registry.register("append_plan_requirement", new AppendRequirementAction(featurePlanStateStore));
        registry.register("append_plan_assumption", new AppendAssumptionAction(featurePlanStateStore));
        registry.register("append_plan_issue", new AppendIssueAction(featurePlanStateStore));
        registry.register("append_plan_validation_note", new AppendValidationNoteAction(featurePlanStateStore));
        registry.register("set_plan_section_status", new SetPlanSectionStatusAction(featurePlanStateStore));
        registry.register("set_solution_outline", new SetSolutionOutlineAction(featurePlanStateStore));
        registry.register("ensure_repo_workspace", new EnsureRepoWorkspaceAction(
                repoWorkspaceService, repoWorkspaceStateStore, featurePlanStateStore, featureRoomStateStore));
        registry.register("launch_cursor_run", new LaunchCursorRunAction(cursorCloudAdapter, stateStore, lifecycleContextStore));
    }

    private void registerTools(ToolRegistry registry) {
        registry.register(new CursorFullRunTool(cursorCloudAdapter, stateStore));
        registry.register(new EchoTool());
    }

    public void shutdown() {
        if (healthServer != null) healthServer.stop();
        var discordAdapter = connectorRegistry.get("discord");
        if (discordAdapter instanceof DiscordConnectorAdapter d) {
            for (DiscordEventSource source : d.getDiscordSources()) {
                if (source != null) source.stop();
            }
        }
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
