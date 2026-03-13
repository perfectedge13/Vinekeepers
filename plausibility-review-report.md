# Plausibility review report — nova-commit phase

**Phase:** plausibility_review  
**Date:** 2026-03-13  
**Registries:** All loaded from specs/specs.yml (core, connectors, env, config, bot, events, state, tools, audit, reasoner, workflow)

## Instructions applied

- For every requirement in every loaded registry, determined **Implemented?** from assets, symbols, tests, and code.
- Legacy `accepted` treated as migration alias only; all requirements in registries use `status: active` or (per schema) draft/deprecated.
- **Failure rule:** Any requirement with status **active** and **Implemented? false** → treat as failure.

---

## Plausibility table

| Requirement ID | Title / short label | Status | Implemented? | Justification |
|----------------|----------------------|--------|---------------|----------------|
| **core-registry.yml** | | | | |
| REQ-CORE-001 | Specs bootstrap and traceability | active | **true** | specs/specs.yml and specs/core-registry.yml exist; npm run validate-specs OK; MANUAL-SPEC-VALIDATE steps satisfiable. |
| REQ-CORE-002 | Application bootstrap and entrypoint | active | **true** | VinekeepersApp loads .env via EnvLoader, creates Bootstrap, loads config; Bootstrap wires engine, config, connectors; VinekeepersAppTest and assets ASSET-APP, ASSET-BOOTSTRAP, ASSET-PACKAGE-MARKER present; mvn test passed. |
| REQ-CORE-003 | Event-driven engine routes events to bots | active | **true** | VinekeepersEngine subscribes to EventBus, routes by Router, runs WorkflowRunner.runResult, reasoner, ToolRunner, sink lifecycle; assets (ASSET-ENGINE, ASSET-ROUTER, etc.) and VinekeepersEngineTest exist; mvn test passed. |
| **connectors-registry.yml** | | | | |
| REQ-CONNECTORS-DISCORD-001 | Discord event source and reply | active | **true** | DiscordEventSource, DiscordAppReplySink, JdaDiscordGateway, DiscordGateway, OutboundDeliveryRouter exist; DiscordEventSourceTest, DiscordAppReplySinkTest, OutboundDeliveryRouterTest, CreateChannelActionTest (lifecycle owner) present; mvn test passed. |
| REQ-CONNECTORS-GITHUB-001 | GitHub event source | active | **true** | GitHubEventSource implements EventSource; asset path exists; MANUAL-CONNECTORS-GITHUB (mvn compile) satisfiable; mvn compile passed. |
| **env-registry.yml** | | | | |
| REQ-ENV-001 | Load .env at startup | active | **true** | EnvLoader.load(".env") called in VinekeepersApp; Env.get(key, default); EnvLoaderTest; assets ASSET-ENV-LOADER, ASSET-ENV, ASSET-APP exist; mvn test passed. |
| **config-registry.yml** | | | | |
| REQ-CONFIG-001 | Load bot config from YAML | active | **true** | ConfigLoader loads YAML; BotConfig, BotDefinition; workflow type/params, discordTokenEnvKey, handlesOwnedSpaces, routing filters; ConfigLoaderTest and related test methods; config/bots.yaml and assets exist; mvn test passed. |
| **bot-registry.yml** | | | | |
| REQ-BOT-001 | Route events to bots by routing rules and ownership | active | **true** | Router.match with LifecycleContextStore and setHandlesOwnedSpacesByBotId; single-owner precedence, filter-based routing, discordMention/discordAuthors; RouterTest and ConfigLoaderTest methods; assets present; mvn test passed. |
| REQ-BOT-002 | Tool policy allow/deny lists | active | **true** | ToolPolicy.allowAll(), allow/deny semantics; ToolPolicyTest; ASSET-TOOL-POLICY exists; mvn test passed. |
| REQ-BOT-003 | Bot definition and persona/model | active | **true** | BotDefinition with persona, model, workflowType/Params, ToolPolicy, handlesOwnedSpaces, discordTokenEnvKey; ConfigLoader produces BotDefinition; MANUAL-BOT-DEF (mvn compile); assets exist; mvn compile passed. |
| **events-registry.yml** | | | | |
| REQ-EVENTS-001 | Event bus publish and subscribe | active | **true** | EventBus.publish/subscribe; Event with sourceId, kind, payload; EventSource, EventSubscriber; EventBusTest; assets exist; mvn test passed. |
| **state-registry.yml** | | | | |
| REQ-STATE-001 | State store for bot workflow state | active | **true** | StateStore.load(botId, conversationKey), save; StateStoreTest; GatheringState; assets exist; mvn test passed. |
| **tools-registry.yml** | | | | |
| REQ-TOOLS-001 | Tool registry and execution | active | **true** | ToolRegistry, ToolRunner, Tool interface; policy enforcement; cursor.fullRun, echo; ToolRunnerTest; assets (Tool, ToolRegistry, ToolRunner, CursorFullRunTool, EchoTool, StubTool, Bootstrap) exist; mvn test passed. |
| **audit-registry.yml** | | | | |
| REQ-AUDIT-001 | Audit log for tool calls and outcomes | active | **true** | AuditLog, AuditRecorder; engine invokes audit; MANUAL-AUDIT (mvn compile); assets exist; mvn compile passed. |
| **reasoner-registry.yml** | | | | |
| REQ-REASONER-001 | Reasoner interface for bot decisions | active | **true** | Reasoner, ReasonerInput, ReasonerOutput, ProposedToolCall, StubReasoner; engine applies state patches and tool proposals; VinekeepersEngineTest.reasonerAppliesStatePatchRunsToolAndRepliesWhenWorkflowIsSilent; assets exist; mvn test passed. |
| **workflow-registry.yml** | | | | |
| REQ-WORKFLOW-001 | Workflow state machine and config-driven runners | active | **true** | Workflow, WorkflowResult, WorkflowRunner, runResult(event, stateStore, botId), WorkflowRunnerFactory (stub, configured), ConfigurableWorkflowRunner, prompt_for_field, capture_field, SessionKeyStrategies, CallActionStep, BranchStep clearKeys, create_thread storeIn; full test set (ConfigurableWorkflowRunnerTest, WorkflowRunnerFactoryTest, StepResultTest, BranchStepTest, CreateThreadActionTest, etc.); assets exist; mvn test passed. |
| REQ-LUNA-001 | Luna bot — Discord mention, multi-turn gather, Cursor Cloud API, lifecycle room Phase 1 | active | **true** | bots.yaml luna + discordMention/discordAuthors, workflowRef luna_cursor; arrietty + arrietty_room, handlesOwnedSpaces; CursorInstructionComposer, launch_cursor_run, LifecycleRunRecord, LifecycleContext, LifecycleContextStore, RuntimeBotInstance; create_channel, post_channel_message, provision_bot_instance, create_lifecycle_context, create_thread, launch_cursor_run; CursorCloudAdapterImpl error parsing, Bearer auth; full test set (CursorInstructionComposerTest, CursorCloudAdapterImplTest, CreateChannelActionTest, CreateLifecycleContextActionTest, LaunchCursorRunActionTest, etc.); mvn test passed. |

---

## Deprecated requirements

None. (No requirement in any loaded registry has `status: deprecated`.)

---

## Validation commands run

- `npm run validate-specs` — **OK**
- `mvn test` — **passed** (exit code 0)
- `mvn compile` — **passed** (exit code 0)

---

## Verdict

**Pass.** All active requirements across all loaded registries have **Implemented? true** with supporting assets, tests, and code. No active requirement is unimplemented; legacy `accepted` does not appear in the registries.
