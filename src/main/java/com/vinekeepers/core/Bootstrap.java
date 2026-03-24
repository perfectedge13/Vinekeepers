package com.vinekeepers.core;

import com.vinekeepers.audit.AuditRecorder;
import com.vinekeepers.bot.BotCatalog;
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
import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.connectors.SpaceOperationsRegistry;
import com.vinekeepers.core.cursor.CursorCloudAdapter;
import com.vinekeepers.core.cursor.CursorCloudAdapterImpl;
import com.vinekeepers.core.cursor.CursorCloudRunMonitor;
import com.vinekeepers.env.Env;
import com.vinekeepers.env.HealthServer;
import com.vinekeepers.events.AsyncEngineEventSubscriber;
import com.vinekeepers.events.EngineEventExecutorFactory;
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
import com.vinekeepers.devops.DeployTargetRegistry;
import com.vinekeepers.devops.HostComposeOpsRunner;
import com.vinekeepers.profile.WorkProfileLoader;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.providers.DeployComposeServicesChoiceProvider;
import com.vinekeepers.providers.DeployTargetsChoiceProvider;
import com.vinekeepers.providers.GitHubReposChoiceProvider;
import com.vinekeepers.providers.GitRemoteBranchesChoiceProvider;
import com.vinekeepers.providers.PlanningClarificationChoiceProvider;
import com.vinekeepers.workflow.DynamicChoiceProviderRegistry;
import com.vinekeepers.workflow.WorkflowActionRegistry;
import com.vinekeepers.workflow.WorkflowRunner;
import com.vinekeepers.workflow.WorkflowRunnerFactory;
import com.vinekeepers.workflow.planning.PlanningCyclePipeline;
import com.vinekeepers.workflow.actions.AcknowledgeReadinessHumanDecisionAction;
import com.vinekeepers.workflow.actions.AppendAssumptionAction;
import com.vinekeepers.workflow.actions.AppendIssueAction;
import com.vinekeepers.workflow.actions.AppendRequirementAction;
import com.vinekeepers.workflow.actions.AppendValidationNoteAction;
import com.vinekeepers.workflow.actions.BuildDiscoveryAgendaAction;
import com.vinekeepers.workflow.actions.BuildInsightDiscoveryAgendaAction;
import com.vinekeepers.workflow.actions.BuildPlanningThreadReviewBodyAction;
import com.vinekeepers.workflow.actions.BuildRequestExplorationAction;
import com.vinekeepers.workflow.actions.BuildRolePlanningThreadMessagesAction;
import com.vinekeepers.workflow.actions.CaptureAndApplyDiscoveryAnswerAction;
import com.vinekeepers.workflow.actions.ClassifyAssumptionOrIssueAction;
import com.vinekeepers.workflow.actions.ClassifyPlanningIngressAction;
import com.vinekeepers.workflow.actions.CoordinatorIntakeBootstrapAction;
import com.vinekeepers.workflow.actions.CreateChannelAction;
import com.vinekeepers.workflow.actions.CreateLifecycleContextAction;
import com.vinekeepers.workflow.actions.CreateThreadAction;
import com.vinekeepers.workflow.actions.DeployResolveProjectAction;
import com.vinekeepers.workflow.actions.DerivePlanGovernanceAction;
import com.vinekeepers.workflow.actions.EvaluatePlanningApprovalGateAction;
import com.vinekeepers.workflow.actions.EvaluatePlanningPacketDepthAction;
import com.vinekeepers.workflow.actions.ExecutePlanningRoomCycleAction;
import com.vinekeepers.workflow.actions.ExpandPlanningDraftsAction;
import com.vinekeepers.workflow.actions.EnsureRepoWorkspaceAction;
import com.vinekeepers.workflow.actions.ResolveDeployBranchAction;
import com.vinekeepers.workflow.actions.RunDeployComposeAction;
import com.vinekeepers.workflow.actions.StartAnsibleDeployAction;
import com.vinekeepers.workflow.actions.GetStructuredDiscoveryGapsAction;
import com.vinekeepers.workflow.actions.HydratePlanningSessionAction;
import com.vinekeepers.workflow.actions.InitializeFeaturePlanStateAction;
import com.vinekeepers.workflow.actions.InitializeFeatureRoomStateAction;
import com.vinekeepers.workflow.actions.LaunchCursorRunAction;
import com.vinekeepers.workflow.actions.MarkIntakeDiscoveryCompleteAction;
import com.vinekeepers.workflow.actions.MergePlanningClarificationChoiceAction;
import com.vinekeepers.workflow.actions.PlanningRunSilentSynthesisAction;
import com.vinekeepers.workflow.actions.PlanningRunEvaluationAction;
import com.vinekeepers.workflow.actions.PrepPlanningRepoGroundingAction;
import com.vinekeepers.workflow.actions.PostPlanningPacketThreadAction;
import com.vinekeepers.workflow.actions.PostPlanningProgressIfChangedAction;
import com.vinekeepers.workflow.actions.PersistPlanApprovalAction;
import com.vinekeepers.workflow.actions.RunArchitectPlanningPassAction;
import com.vinekeepers.workflow.actions.RunAuditorPlanningPassAction;
import com.vinekeepers.workflow.actions.RunLlmPlanningSynthesisAction;
import com.vinekeepers.workflow.actions.RunRequestExpansionLlmAction;
import com.vinekeepers.workflow.actions.RunScribePlanningPassAction;
import com.vinekeepers.workflow.actions.RunPlanCritiqueAndReadinessAction;
import com.vinekeepers.workflow.actions.OpenAiPlanningProgressPoster;
import com.vinekeepers.workflow.actions.PostChannelMessageAction;
import com.vinekeepers.workflow.actions.ProvisionBotInstanceAction;
import com.vinekeepers.workflow.actions.ProvisionRoomParticipantsAction;
import com.vinekeepers.workflow.actions.RecomputePlanProgressAction;
import com.vinekeepers.workflow.actions.SetPlanSectionStatusAction;
import com.vinekeepers.workflow.actions.SetPlanningIntakeStageAction;
import com.vinekeepers.workflow.actions.SetSolutionOutlineAction;
import com.vinekeepers.workflow.actions.StartCoordinatorPlanningAction;
import com.vinekeepers.workflow.actions.SynthesizePlanDraftsAction;
import com.vinekeepers.workflow.actions.SpreadPlanningWorkspaceBlockerAction;
import com.vinekeepers.workflow.actions.SpreadPlanWorkspaceSignalsAction;
import com.vinekeepers.workflow.actions.SynthesizePreCritiqueArtifactsAction;
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
import java.util.concurrent.ExecutorService;

/**
 * Wires EventBus, Engine, Router, connectors, and optional YAML config.
 */
public final class Bootstrap {

    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);

    private final EventBus eventBus;
    private final ExecutorService engineEventExecutor;
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
    private final BotCatalog botCatalog = new BotCatalog();
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
        actionRegistry.register("resolve_deploy_branch", new ResolveDeployBranchAction());
        Path deployManifestPath = DeployTargetRegistry.resolveManifestPath();
        DeployTargetRegistry bootstrapTargets = DeployTargetRegistry.load(deployManifestPath);
        HostComposeOpsRunner.logContainerDirectComposeWarnings(bootstrapTargets, deployManifestPath);
        actionRegistry.register("deploy_resolve_project", new DeployResolveProjectAction(bootstrapTargets));
        actionRegistry.register("start_ansible_deploy", new StartAnsibleDeployAction(outboundDeliveryRouter, bootstrapTargets));
        actionRegistry.register("run_deploy_compose", new RunDeployComposeAction(outboundDeliveryRouter, bootstrapTargets));
        AuditRecorder audit = entry -> log.info("Audit: {} {} {} {}", entry.getTimestamp(), entry.getBotId(), entry.getAction(), entry.getDetail());
        this.router = new Router(lifecycleContextStore, featureRoomStateStore, featurePlanStateStore, "");
        this.engine = new VinekeepersEngine(router, stateStore, audit, toolRunner);
        this.engineEventExecutor = EngineEventExecutorFactory.create();
        actionRegistry.register(
                "start_coordinator_planning",
                new StartCoordinatorPlanningAction(engine, featureRoomStateStore, featurePlanStateStore));
        eventBus.subscribe(new AsyncEngineEventSubscriber(engine, engineEventExecutor));
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
            choiceProviderRegistry.register("planningClarification", new PlanningClarificationChoiceProvider());
            DeployTargetRegistry deployTargetRegistry = DeployTargetRegistry.load(DeployTargetRegistry.resolveManifestPath());
            choiceProviderRegistry.register("deployTargets", new DeployTargetsChoiceProvider(deployTargetRegistry));
            choiceProviderRegistry.register("deployComposeServices", new DeployComposeServicesChoiceProvider(deployTargetRegistry));
            choiceProviderRegistry.register("deployGitBranches", new GitRemoteBranchesChoiceProvider(deployTargetRegistry));
            List<BotDefinition> bots = loader.buildBots(config);
            lastLoadedBots.clear();
            lastLoadedBots.addAll(bots);
            botCatalog.replaceAll(bots);
            Map<String, Boolean> handlesMap = new HashMap<>();
            for (BotDefinition bot : bots) {
                boolean handles = bot.getConnectorIdentity("discord")
                        .map(d -> d.getBoolean("handlesOwnedSpaces"))
                        .orElse(false);
                handlesMap.put(bot.getId(), handles);
            }
            router.setHandlesOwnedSpacesByBotId(handlesMap);
            String planningCoordinatorFallback = null;
            for (BotDefinition bot : bots) {
                if ("arrietty_room_v2".equals(bot.getWorkflowType())
                        && Boolean.TRUE.equals(handlesMap.get(bot.getId()))) {
                    planningCoordinatorFallback = bot.getId();
                    break;
                }
            }
            if (planningCoordinatorFallback == null) {
                for (BotDefinition bot : bots) {
                    if (Boolean.TRUE.equals(handlesMap.get(bot.getId()))) {
                        planningCoordinatorFallback = bot.getId();
                        break;
                    }
                }
            }
            router.setPlanningCoordinatorFallbackBotId(
                    planningCoordinatorFallback != null ? planningCoordinatorFallback : "");
            for (BotDefinition bot : bots) {
                engine.registerBot(bot);
                WorkflowRunner runner = WorkflowRunnerFactory.create(bot, config.getWorkflows(), this.actionRegistry, toolRunner, choiceProviderRegistry);
                engine.registerRunner(bot.getId(), runner);
                engine.registerReasoner(bot.getId(), new StubReasoner());
            }
            actionRegistry.register("deploy_resolve_project", new DeployResolveProjectAction(deployTargetRegistry));
            actionRegistry.register("start_ansible_deploy", new StartAnsibleDeployAction(outboundDeliveryRouter, deployTargetRegistry));
            actionRegistry.register("run_deploy_compose", new RunDeployComposeAction(outboundDeliveryRouter, deployTargetRegistry));
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
        ConnectorContext context =
                new ConnectorContext(
                        eventBus, outboundDeliveryRouter, featureRoomStateStore, lifecycleContextStore, featurePlanStateStore);
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
        PostChannelMessageAction postChannelMessageAction = new PostChannelMessageAction(outboundDeliveryRouter);
        actionRegistry.register("post_channel_message", postChannelMessageAction);
        actionRegistry.register(
                "post_planning_progress_if_changed", new PostPlanningProgressIfChangedAction(postChannelMessageAction));
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
        PostChannelMessageAction openAiProgressChannelPost = new PostChannelMessageAction(outboundDeliveryRouter);
        OpenAiChatClient openAiChatClient =
                new OpenAiChatClient(new OpenAiPlanningProgressPoster(openAiProgressChannelPost));
        PlanningCyclePipeline planningCyclePipeline =
                new PlanningCyclePipeline(openAiChatClient, featurePlanStateStore, workProfileRegistry);
        registry.register(
                "v2_noop",
                (event, state, bind) -> java.util.Map.of("v2NoopRan", "true"));
        registry.register("provision_bot_instance", new ProvisionBotInstanceAction(stateStore));
        registry.register("create_lifecycle_context", new CreateLifecycleContextAction(lifecycleContextStore));
        registry.register("provision_room_participants", new ProvisionRoomParticipantsAction(stateStore, botCatalog));
        registry.register("initialize_feature_room_state", new InitializeFeatureRoomStateAction(featureRoomStateStore));
        registry.register("initialize_feature_plan_state", new InitializeFeaturePlanStateAction(
                featurePlanStateStore, featureRoomStateStore, workProfileRegistry));
        registry.register("hydrate_planning_session", new HydratePlanningSessionAction(featureRoomStateStore, featurePlanStateStore));
        registry.register("classify_planning_ingress", new ClassifyPlanningIngressAction());
        registry.register("synthesize_plan_drafts", new SynthesizePlanDraftsAction(featurePlanStateStore, workProfileRegistry));
        registry.register("upsert_artifact_section_data", new UpsertArtifactSectionDataAction(featurePlanStateStore, workProfileRegistry));
        registry.register("get_structured_discovery_gaps", new GetStructuredDiscoveryGapsAction(featurePlanStateStore, workProfileRegistry));
        registry.register("build_discovery_agenda", new BuildDiscoveryAgendaAction());
        registry.register(
                "build_insight_discovery_agenda",
                new BuildInsightDiscoveryAgendaAction(featurePlanStateStore, workProfileRegistry));
        registry.register(
                "synthesize_pre_critique_artifacts",
                new SynthesizePreCritiqueArtifactsAction(featurePlanStateStore, workProfileRegistry));
        registry.register(
                "build_request_exploration",
                new BuildRequestExplorationAction(featurePlanStateStore, workProfileRegistry));
        registry.register("capture_and_apply_discovery_answer", new CaptureAndApplyDiscoveryAnswerAction(featurePlanStateStore, workProfileRegistry));
        registry.register("classify_assumption_or_issue", new ClassifyAssumptionOrIssueAction(featurePlanStateStore));
        registry.register("recompute_plan_progress", new RecomputePlanProgressAction(featurePlanStateStore, workProfileRegistry));
        registry.register("mark_intake_discovery_complete", new MarkIntakeDiscoveryCompleteAction(featurePlanStateStore));
        registry.register("set_planning_intake_stage", new SetPlanningIntakeStageAction(featurePlanStateStore));
        registry.register(
                "coordinator_intake_bootstrap",
                new CoordinatorIntakeBootstrapAction(outboundDeliveryRouter, featurePlanStateStore));
        registry.register("acknowledge_readiness_human_decision", new AcknowledgeReadinessHumanDecisionAction(
                featurePlanStateStore, workProfileRegistry));
        registry.register("build_role_planning_thread_messages", new BuildRolePlanningThreadMessagesAction(featurePlanStateStore));
        registry.register("derive_plan_governance", new DerivePlanGovernanceAction(featurePlanStateStore, workProfileRegistry));
        registry.register(
                "run_plan_critique_and_readiness",
                new RunPlanCritiqueAndReadinessAction(openAiChatClient, featurePlanStateStore, workProfileRegistry));
        registry.register("build_planning_thread_review_body", new BuildPlanningThreadReviewBodyAction(featurePlanStateStore));
        registry.register(
                "post_planning_packet_thread",
                new PostPlanningPacketThreadAction(outboundDeliveryRouter, featurePlanStateStore));
        registry.register("expand_planning_drafts", new ExpandPlanningDraftsAction(featurePlanStateStore, workProfileRegistry));
        registry.register("persist_plan_approval", new PersistPlanApprovalAction(featurePlanStateStore));
        registry.register("append_plan_requirement", new AppendRequirementAction(featurePlanStateStore));
        registry.register("append_plan_assumption", new AppendAssumptionAction(featurePlanStateStore));
        registry.register("append_plan_issue", new AppendIssueAction(featurePlanStateStore));
        registry.register("append_plan_validation_note", new AppendValidationNoteAction(featurePlanStateStore));
        registry.register("set_plan_section_status", new SetPlanSectionStatusAction(featurePlanStateStore));
        registry.register("set_solution_outline", new SetSolutionOutlineAction(featurePlanStateStore));
        registry.register("ensure_repo_workspace", new EnsureRepoWorkspaceAction(
                repoWorkspaceService, repoWorkspaceStateStore, featurePlanStateStore, featureRoomStateStore,
                botCatalog,
                outboundDeliveryRouter));
        registry.register(
                "prep_planning_repo_grounding",
                new PrepPlanningRepoGroundingAction(openAiChatClient, featurePlanStateStore));
        registry.register("spread_plan_workspace_signals", new SpreadPlanWorkspaceSignalsAction(featurePlanStateStore));
        registry.register(
                "spread_planning_workspace_blocker",
                new SpreadPlanningWorkspaceBlockerAction(featurePlanStateStore));
        registry.register(
                "evaluate_planning_packet_depth",
                new EvaluatePlanningPacketDepthAction(featurePlanStateStore, workProfileRegistry));
        registry.register("evaluate_planning_approval_gate", new EvaluatePlanningApprovalGateAction(featurePlanStateStore));
        registry.register("execute_planning_room_cycle", new ExecutePlanningRoomCycleAction(planningCyclePipeline));
        PlanningRunSilentSynthesisAction planningSilentSynthesis =
                new PlanningRunSilentSynthesisAction(planningCyclePipeline);
        PlanningRunEvaluationAction planningEvaluation = new PlanningRunEvaluationAction(planningCyclePipeline);
        registry.register("planning_run_silent_synthesis", planningSilentSynthesis);
        registry.register("planning_run_evaluation", planningEvaluation);
        registry.register(
                "merge_planning_clarification_choice",
                new MergePlanningClarificationChoiceAction(featurePlanStateStore, workProfileRegistry));
        registry.register(
                "run_arrietty_planning_pass",
                new RunArchitectPlanningPassAction(openAiChatClient, featurePlanStateStore, workProfileRegistry));
        registry.register(
                "run_architect_planning_pass",
                new RunArchitectPlanningPassAction(openAiChatClient, featurePlanStateStore, workProfileRegistry));
        registry.register(
                "run_planning_pass",
                new RunArchitectPlanningPassAction(openAiChatClient, featurePlanStateStore, workProfileRegistry));
        registry.register(
                "run_auditor_planning_pass",
                new RunAuditorPlanningPassAction(openAiChatClient, featurePlanStateStore, workProfileRegistry));
        registry.register(
                "run_scribe_planning_pass",
                new RunScribePlanningPassAction(openAiChatClient, featurePlanStateStore, workProfileRegistry));
        registry.register(
                "run_llm_planning_synthesis",
                new RunLlmPlanningSynthesisAction(openAiChatClient, featurePlanStateStore, workProfileRegistry));
        registry.register(
                "run_request_expansion_llm",
                new RunRequestExpansionLlmAction(openAiChatClient, featurePlanStateStore, workProfileRegistry));
        registry.register("launch_cursor_run", new LaunchCursorRunAction(cursorCloudAdapter, stateStore, lifecycleContextStore, featurePlanStateStore));
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
        EngineEventExecutorFactory.shutdownQuietly(engineEventExecutor);
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
