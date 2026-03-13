# Plausibility review report

**Phase:** plausibility_review  
**Date:** 2026-03-10  
**Registries:** core-registry.yml, connectors-registry.yml

## Plausibility table

| Requirement ID | Title / short label | Status | Implemented? | Justification |
|----------------|---------------------|--------|--------------|---------------|
| REQ-CORE-001 | Specs bootstrap and traceability | active | **true** | specs/specs.yml and specs/core-registry.yml exist; MANUAL-SPEC-VALIDATE steps (Test-Path) and npm run validate-specs in project; assets ASSET-SPEC-INDEX, ASSET-REGISTRY present. |
| REQ-ENV-001 | Load .env at startup | active | **true** | EnvLoader.java, Env.java, VinekeepersApp.java exist; EnvLoaderTest in validation; VinekeepersApp calls EnvLoader.load before Bootstrap. |
| REQ-CORE-002 | Application bootstrap and entrypoint | active | **true** | VinekeepersApp.java, Bootstrap.java, PackageMarker exist; VinekeepersAppTest passes; Bootstrap wires engine, config, WorkflowRunnerFactory, ToolRunner. |
| REQ-CORE-003 | Event-driven engine routes events to bots | active | **true** | VinekeepersEngine.java, Router, OutboundResponse, AppReplySink, ReplyTarget, Capabilities, ResponseIntent exist; VinekeepersEngineTest passes; engine subscribes to bus, routes, runs workflow/reasoner, delivers via sink. |
| REQ-CONFIG-001 | Load bot config from YAML | active | **true** | ConfigLoader.java, BotConfig.java, config/bots.yaml exist; ConfigLoaderTest and buildRouterParsesDiscordMentionRouting, buildRouterParsesDiscordAuthorsRouting pass; workflow block, routing filters, step bind parsed. |
| REQ-BOT-001 | Route events to bots by routing rules | active | **true** | Router, Routing, EventFilter, RoutingFilter, NormalizedEventContext exist; RouterTest and ConfigLoaderTest methods for mention/authors/interaction pass; discordTrigger/discordMention/discordAuthors enforced as specified. |
| REQ-BOT-002 | Tool policy allow/deny lists | active | **true** | ToolPolicy.java exists; ToolPolicyTest passes; allowAll, allowlist, deny precedence implemented. |
| REQ-BOT-003 | Bot definition and persona/model | active | **true** | BotDefinition, ConversationMode, Persona, ModelProfile, MemoryPolicy exist; MANUAL-BOT-DEF (mvn compile) passes; ConfigLoader produces BotDefinition from YAML. |
| REQ-EVENTS-001 | Event bus publish and subscribe | active | **true** | EventBus, Event, EventSource, EventSubscriber exist; EventBusTest passes; publish/subscribe and Event sourceId, kind, payload. |
| REQ-STATE-001 | State store for bot workflow state | active | **true** | StateStore.java, GatheringState (Luna state) exist; StateStoreTest passes; load/save per bot and conversation key. |
| REQ-TOOLS-001 | Tool registry and execution | active | **true** | Tool, ToolRegistry, ToolRunner, CursorFullRunTool, EchoTool, StubTool, Bootstrap wiring exist; ToolRunnerTest and MANUAL-TOOLS pass; policy enforcement and cursor.fullRun/echo. |
| REQ-AUDIT-001 | Audit log for tool calls and outcomes | active | **true** | AuditLog, AuditRecorder exist; MANUAL-AUDIT (mvn compile) passes; engine invokes audit. |
| REQ-REASONER-001 | Reasoner interface for bot decisions | active | **true** | Reasoner, ReasonerInput, ReasonerOutput, ProposedToolCall, StubReasoner, Engine integration exist; reasonerAppliesStatePatchRunsToolAndRepliesWhenWorkflowIsSilent and MANUAL-REASONER pass. |
| REQ-WORKFLOW-001 | Workflow state machine and config-driven runners | active | **true** | Workflow, WorkflowResult, WorkflowRunner, WorkflowRunnerFactory, ConfigurableWorkflowRunner, step/action assets exist; ConfigurableWorkflowRunnerTest, WorkflowRunnerFactoryTest, StepResultTest, BranchStepTest, CallActionStepTest, etc. pass; session keys, clearKeys, bind precedence. |
| REQ-LUNA-001 | Luna bot — Discord mention, multi-turn gather, Cursor Cloud API, lifecycle room Phase 1 | active | **true** | luna in bots.yaml with discordMention/discordAuthors, workflowRef luna_cursor; CursorInstructionComposer, CursorCloudAdapter(Impl), LifecycleRunRecord, CursorCloudRunMonitor, lifecycle actions (create_channel, post_channel_message, provision_bot_instance, create_lifecycle_context, launch_cursor_run) exist; CursorInstructionComposerTest, CursorCloudAdapterImplTest (incl. error parsing, Bearer, no key log), CreateChannelActionTest (sentinel, normalize), PostChannelMessageActionTest, CreateLifecycleContextActionTest, LaunchCursorRunActionTest, etc. pass. |
| REQ-CONNECTORS-DISCORD-001 | Discord event source and reply | active | **true** | DiscordEventSource, DiscordReplySender, JdaDiscordGateway, DiscordGateway, DiscordAppReplySink exist; DiscordEventSourceTest, DiscordAppReplySinkTest, CreateChannelActionTest, lunaWorkflowWithReplySenderSendsReplyToDiscord pass; EventSource, mention metadata, lifecycle ops, createTextChannel. |
| REQ-CONNECTORS-GITHUB-001 | GitHub event source | active | **true** | GitHubEventSource.java exists; MANUAL-CONNECTORS-GITHUB (mvn compile) passes; implements EventSource, emits to bus. |

## Result

**Pass.** All active requirements have **Implemented? true**. No deprecated requirements in scope; no failures.

**Summary:** All active requirements across core and connectors registries are implemented; traceability assets exist, referenced unit and manual tests pass (mvn test exit 0), and code/config align with acceptance criteria.
