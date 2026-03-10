# Nova-spec scan output

**Status:** Pass  
**Spec index:** specs/specs.yml  
**Registries:** core-registry.yml, connectors-registry.yml  
**Scan roots:** root_config_files [pom.xml, README.md, .env.example], source_dirs [src/main/java, src/test/java], bots_config config/bots.yaml

---

## 1.1 Enumerate paths

### Source
- **src/main/java:** 99 `.java` files under `com/vinekeepers/` (packages: root, config, core, core/cursor, bot, events, state, tools, audit, reasoner, workflow, workflow/steps, env, connectors, interactions, providers, util).
- **src/test/java:** 31 `.java` test files mirroring production packages.

### Specs
- **Spec index:** specs/specs.yml
- **Registry YAML:** specs/core-registry.yml, specs/connectors-registry.yml
- **Schema:** specs/schema/specs-index.schema.json, specs/schema/req-registry.schema.json

### Cursor
- **Rules:** .cursor/rules/ (guardrails.mdc, vinekeepers-standards.mdc, spec-workflow.mdc, spec-workflow-core.mdc, spec-workflow-vinekeepers.mdc)
- **Skills:** .cursor/skills/ (nova-spec, nova-code, nova-commit, nova-pr, nova-grafana, nova-wiki, common, vinekeepers-code)
- **Workflows:** .cursor/workflows/ (nova-spec.yml, nova-code.yml, nova-commit.yml, nova-pr.yml, nova-wiki.yml, nova-mk.yml, reports/)

### Root/config
- **Root:** pom.xml, README.md, .env.example
- **Bots config:** config/bots.yaml

---

## 1.2 Derive structure

### Packages and entrypoints
- **Packages:** com.vinekeepers, com.vinekeepers.config, com.vinekeepers.core, com.vinekeepers.core.cursor, com.vinekeepers.bot, com.vinekeepers.events, com.vinekeepers.state, com.vinekeepers.tools, com.vinekeepers.audit, com.vinekeepers.reasoner, com.vinekeepers.workflow, com.vinekeepers.workflow.steps, com.vinekeepers.env, com.vinekeepers.connectors, com.vinekeepers.interactions, com.vinekeepers.providers, com.vinekeepers.util
- **Entrypoint:** com.vinekeepers.VinekeepersApp (public static void main; loads .env, creates Bootstrap, starts engine)

### Test → production mapping
| Test class | Production target |
|------------|-------------------|
| VinekeepersAppTest | VinekeepersApp |
| EnvLoaderTest | EnvLoader |
| ConfigLoaderTest | ConfigLoader |
| RouterTest | Router |
| NormalizedEventContextTest | NormalizedEventContext |
| ToolPolicyTest | ToolPolicy |
| EventBusTest | EventBus |
| StateStoreTest | StateStore |
| ToolRunnerTest | ToolRunner |
| VinekeepersEngineTest | VinekeepersEngine |
| DiscordEventSourceTest | DiscordEventSource |
| DiscordAppReplySinkTest | DiscordAppReplySink |
| CursorCloudAdapterImplTest | CursorCloudAdapterImpl |
| CursorCloudRunMonitorTest | CursorCloudRunMonitor |
| CursorFullRunToolTest | CursorFullRunTool |
| ConfigurableWorkflowRunnerTest | ConfigurableWorkflowRunner |
| ConfigurableWorkflowStateTest | ConfigurableWorkflowState |
| WorkflowRunnerFactoryTest | WorkflowRunnerFactory |
| WorkflowDefinitionTest | WorkflowDefinition |
| WorkflowActionRegistryTest | WorkflowActionRegistry |
| WorkflowRunResultTest | WorkflowRunResult |
| StepResultTest | StepResult |
| BranchStepTest | BranchStep |
| AskForInputStepTest | AskForInputStep |
| CallActionStepTest | CallActionStep |
| DoneStepTest | DoneStep |
| CaptureFieldFromEventStepTest | CaptureFieldFromEventStep |
| CursorCloudGatheringWorkflowTest | CursorCloudGatheringWorkflow |
| GatheringStateTest | GatheringState |
| OutboundResponseTest | OutboundResponse |
| CapabilitiesTest | Capabilities |

### Key config/spec roles
- **Spec index:** specs/specs.yml (scope, change_triggers, specs[], domains, interfaces, validation)
- **Registries:** specs/core-registry.yml (core/env/config/bot/events/state/tools/audit/reasoner/workflow), specs/connectors-registry.yml (connectors: Discord, GitHub)
- **Schemas:** specs/schema/specs-index.schema.json, specs/schema/req-registry.schema.json

---

## 1.3 Candidate lists (internal handoff)

### Candidate assets (brief)
Existing registry assets are the authoritative list. One additional file not in registry:
- **path:** src/main/java/com/vinekeepers/env/HealthServer.java  
  **role:** Health check HTTP server (GET /, GET /health)  
  **theme:** env / health; optional candidate for REQ-ENV or a future health requirement.

All other significant main classes, config files, and spec files are already listed in core-registry.yml and connectors-registry.yml (ASSET-* ids).

### Candidate requirements (brief)
Existing requirements in core-registry.yml and connectors-registry.yml cover:
- REQ-CORE-001, REQ-CORE-002, REQ-CORE-003 (specs bootstrap, app bootstrap, event-driven engine)
- REQ-ENV-001 (load .env at startup)
- REQ-CONFIG-001 (load bot config from YAML)
- REQ-BOT-001, REQ-BOT-002, REQ-BOT-003 (routing, tool policy, bot definition)
- REQ-EVENTS-001 (event bus)
- REQ-STATE-001 (state store)
- REQ-TOOLS-001 (tool registry and execution)
- REQ-AUDIT-001 (audit log)
- REQ-REASONER-001 (reasoner interface)
- REQ-WORKFLOW-001 (workflow state machine and config-driven runners)
- REQ-LUNA-001 (Luna bot — Discord mention, multi-turn gather, Cursor Cloud API; bot-instance, not a feature slug)
- REQ-CONNECTORS-DISCORD-001, REQ-CONNECTORS-GITHUB-001 (Discord and GitHub connectors)

No new requirement themes inferred beyond these; structure and README align with existing IDs.

### Current spec state summary
- **Existing requirement ids:** REQ-CORE-001, REQ-CORE-002, REQ-CORE-003, REQ-ENV-001, REQ-CONFIG-001, REQ-BOT-001, REQ-BOT-002, REQ-BOT-003, REQ-EVENTS-001, REQ-STATE-001, REQ-TOOLS-001, REQ-AUDIT-001, REQ-REASONER-001, REQ-WORKFLOW-001, REQ-LUNA-001 (core-registry); REQ-CONNECTORS-DISCORD-001, REQ-CONNECTORS-GITHUB-001 (connectors-registry).
- **Existing asset ids (core):** ASSET-SPEC-INDEX, ASSET-REGISTRY, ASSET-APP, ASSET-BOOTSTRAP, ASSET-ENV-LOADER, ASSET-ENV, ASSET-ENGINE, ASSET-OUTBOUND-RESPONSE, ASSET-APP-REPLY-SINK, ASSET-REPLY-TARGET, ASSET-CAPABILITIES, ASSET-RESPONSE-INTENT, ASSET-CONFIG-LOADER, ASSET-BOTS-YAML, ASSET-BOT-CONFIG, ASSET-ROUTER, ASSET-ROUTING, ASSET-EVENT-FILTER, ASSET-ROUTING-FILTER, ASSET-NORMALIZED-EVENT-CONTEXT, ASSET-TOOL-POLICY, ASSET-BOT-DEFINITION, ASSET-CONVERSATION-MODE, ASSET-PERSONA, ASSET-MODEL-PROFILE, ASSET-MEMORY-POLICY, ASSET-EVENT-BUS, ASSET-EVENT, ASSET-EVENT-SOURCE, ASSET-EVENT-SUBSCRIBER, ASSET-STATE-STORE, ASSET-TOOL, ASSET-TOOL-REGISTRY, ASSET-TOOL-RUNNER, ASSET-CURSOR-FULL-RUN-TOOL, ASSET-ECHO-TOOL, ASSET-STUB-TOOL, ASSET-AUDIT-LOG, ASSET-AUDIT-RECORDER, ASSET-REASONER, ASSET-REASONER-INPUT, ASSET-REASONER-OUTPUT, ASSET-PROPOSED-TOOL-CALL, ASSET-STUB-REASONER, ASSET-WORKFLOW, ASSET-WORKFLOW-RESULT, ASSET-STUB-WORKFLOW, ASSET-STUB-STATE, ASSET-LUNA-STATE, ASSET-LUNA-WORKFLOW, ASSET-WORKFLOW-RUNNER, ASSET-WORKFLOW-RUN-RESULT, ASSET-STUB-WORKFLOW-RUNNER, ASSET-WORKFLOW-RUNNER-FACTORY, ASSET-SESSION-KEY-STRATEGY, ASSET-SESSION-KEY-STRATEGIES, ASSET-STEP-RESULT, ASSET-STEP-OUTCOME, ASSET-WORKFLOW-STEP, ASSET-WORKFLOW-ACTION, ASSET-WORKFLOW-ACTION-REGISTRY, ASSET-CONFIGURABLE-WORKFLOW-STATE, ASSET-WORKFLOW-DEFINITION, ASSET-CONFIGURABLE-WORKFLOW-RUNNER, ASSET-ASK-FOR-INPUT-STEP, ASSET-PROMPT-FOR-FIELD-STEP, ASSET-CAPTURE-FIELD-STEP, ASSET-CALL-ACTION-STEP, ASSET-BRANCH-STEP, ASSET-DYNAMIC-CHOICE-PROVIDER, ASSET-DYNAMIC-CHOICE-PROVIDER-REGISTRY, ASSET-GITHUB-REPOS-CHOICE-PROVIDER, ASSET-DONE-STEP, ASSET-CURSOR-ADAPTER, ASSET-CURSOR-ADAPTER-IMPL, ASSET-LUNA-RUN-STATE, ASSET-CURSOR-RUN-MONITOR, ASSET-CURSOR-AGENT-LAUNCH-REQUEST, ASSET-CURSOR-AGENT-CONVERSATION, ASSET-CURSOR-AGENT-DETAILS, ASSET-CURSOR-AGENT-LAUNCH-RESULT, ASSET-CURSOR-AGENT-MESSAGE, ASSET-CURSOR-CLOUD-TRANSPORT, ASSET-CURSOR-CLOUD-TRANSPORT-RESPONSE, ASSET-CURSOR-CLOUD-EXCEPTION, ASSET-PACKAGE-MARKER.
- **Existing asset ids (connectors):** ASSET-DISCORD-SOURCE, ASSET-DISCORD-REPLY, ASSET-DISCORD-GATEWAY, ASSET-DISCORD-GATEWAY-CONTRACT, ASSET-DISCORD-REPLY-SINK, ASSET-GITHUB-SOURCE.
- **Traceability:** requirements.traceability.assets and assets.requires/feature_ids are populated in both registries.
- **validation.tests:** Both registries define validation.tests with testClass/testMethod where applicable; specs.yml interfaces reference npm run validate-specs, validate-drift, mvn test, mvn compile, validate-docs.
- **Bot ids from config/bots.yaml:** bots[].id = luna (not used as feature slug; REQ-LUNA-001 and related assets are grouped under cursor-gathering / workflow / config / bot features).

---

## 1.4 Candidate features (for orchestrator 2.1–2.4)

Each entry: slug, title, requirement_ids[], asset_ids[], suggested_domain.  
Finer-grained by logical area; connector features use suggested_domain **connectors**; all others use **core** or the logical domain slug that maps into core.  
Bot id **luna** is not a feature slug; Luna-related content is in cursor-gathering, workflow, config, bot.

| slug | title | requirement_ids | asset_ids | suggested_domain |
|------|--------|-----------------|-----------|------------------|
| env | Environment and .env loading | [REQ-ENV-001] | [ASSET-ENV-LOADER, ASSET-ENV, ASSET-APP] | core |
| config | Bot config from YAML | [REQ-CONFIG-001] | [ASSET-CONFIG-LOADER, ASSET-BOT-CONFIG, ASSET-BOTS-YAML] | core |
| routing | Event routing and normalized context | [REQ-BOT-001] | [ASSET-ROUTER, ASSET-ROUTING, ASSET-EVENT-FILTER, ASSET-ROUTING-FILTER, ASSET-NORMALIZED-EVENT-CONTEXT] | core |
| bot | Bot runtime definition and tool policy | [REQ-BOT-002, REQ-BOT-003] | [ASSET-TOOL-POLICY, ASSET-BOT-DEFINITION, ASSET-CONVERSATION-MODE, ASSET-PERSONA, ASSET-MODEL-PROFILE, ASSET-MEMORY-POLICY] | core |
| events | Event bus and publish/subscribe | [REQ-EVENTS-001] | [ASSET-EVENT-BUS, ASSET-EVENT, ASSET-EVENT-SOURCE, ASSET-EVENT-SUBSCRIBER] | core |
| state | State store for workflow state | [REQ-STATE-001] | [ASSET-STATE-STORE, ASSET-LUNA-STATE] | core |
| tools | Tool registry and execution | [REQ-TOOLS-001] | [ASSET-TOOL, ASSET-TOOL-REGISTRY, ASSET-TOOL-RUNNER, ASSET-CURSOR-FULL-RUN-TOOL, ASSET-ECHO-TOOL, ASSET-STUB-TOOL, ASSET-BOOTSTRAP] | core |
| audit | Audit log for tool calls and outcomes | [REQ-AUDIT-001] | [ASSET-AUDIT-LOG, ASSET-AUDIT-RECORDER] | core |
| reasoner | Reasoner interface for bot decisions | [REQ-REASONER-001] | [ASSET-REASONER, ASSET-REASONER-INPUT, ASSET-REASONER-OUTPUT, ASSET-PROPOSED-TOOL-CALL, ASSET-STUB-REASONER, ASSET-ENGINE] | core |
| workflow | Workflow runners and session lifecycle | [REQ-WORKFLOW-001] | [ASSET-WORKFLOW, ASSET-WORKFLOW-RESULT, ASSET-STUB-WORKFLOW, ASSET-STUB-STATE, ASSET-WORKFLOW-RUNNER, ASSET-WORKFLOW-RUN-RESULT, ASSET-STUB-WORKFLOW-RUNNER, ASSET-WORKFLOW-RUNNER-FACTORY, ASSET-SESSION-KEY-STRATEGY, ASSET-SESSION-KEY-STRATEGIES, ASSET-CONFIGURABLE-WORKFLOW-STATE, ASSET-CONFIGURABLE-WORKFLOW-RUNNER, ASSET-DYNAMIC-CHOICE-PROVIDER-REGISTRY] | core |
| workflow-steps | Workflow step DSL and branching actions | [REQ-WORKFLOW-001] | [ASSET-STEP-RESULT, ASSET-STEP-OUTCOME, ASSET-WORKFLOW-STEP, ASSET-WORKFLOW-ACTION, ASSET-WORKFLOW-ACTION-REGISTRY, ASSET-WORKFLOW-DEFINITION, ASSET-ASK-FOR-INPUT-STEP, ASSET-PROMPT-FOR-FIELD-STEP, ASSET-CAPTURE-FIELD-STEP, ASSET-CALL-ACTION-STEP, ASSET-BRANCH-STEP, ASSET-DYNAMIC-CHOICE-PROVIDER, ASSET-DONE-STEP] | core |
| cursor-gathering | Cursor-backed gathering workflow | [REQ-LUNA-001] | [ASSET-LUNA-STATE, ASSET-LUNA-WORKFLOW, ASSET-CURSOR-ADAPTER, ASSET-CURSOR-ADAPTER-IMPL, ASSET-LUNA-RUN-STATE, ASSET-CURSOR-RUN-MONITOR, ASSET-CURSOR-AGENT-LAUNCH-REQUEST, ASSET-CURSOR-AGENT-CONVERSATION, ASSET-CURSOR-AGENT-DETAILS, ASSET-CURSOR-AGENT-LAUNCH-RESULT, ASSET-CURSOR-AGENT-MESSAGE, ASSET-CURSOR-CLOUD-TRANSPORT, ASSET-CURSOR-CLOUD-TRANSPORT-RESPONSE, ASSET-CURSOR-CLOUD-EXCEPTION, ASSET-CURSOR-FULL-RUN-TOOL, ASSET-GITHUB-REPOS-CHOICE-PROVIDER, ASSET-BOTS-YAML, ASSET-BOOTSTRAP, ASSET-ENGINE] | core |
| core | Specs governance and bootstrap | [REQ-CORE-001, REQ-CORE-002] | [ASSET-SPEC-INDEX, ASSET-REGISTRY, ASSET-APP, ASSET-BOOTSTRAP, ASSET-PACKAGE-MARKER] | core |
| engine | Event-driven engine orchestration | [REQ-CORE-003] | [ASSET-ENGINE, ASSET-WORKFLOW-RUNNER, ASSET-TOOL-RUNNER, ASSET-REASONER-INPUT, ASSET-REASONER-OUTPUT, ASSET-OUTBOUND-RESPONSE, ASSET-APP-REPLY-SINK, ASSET-REPLY-TARGET, ASSET-CAPABILITIES, ASSET-RESPONSE-INTENT] | core |
| discord | Discord event source and reply | [REQ-CONNECTORS-DISCORD-001] | [ASSET-DISCORD-SOURCE, ASSET-DISCORD-REPLY, ASSET-DISCORD-GATEWAY, ASSET-DISCORD-GATEWAY-CONTRACT, ASSET-DISCORD-REPLY-SINK] | connectors |
| github | GitHub event source | [REQ-CONNECTORS-GITHUB-001] | [ASSET-GITHUB-SOURCE] | connectors |

---

## Return summary

- **Pass:** Yes.
- **Candidate features:** 16 (14 core-domain, 2 connectors-domain); slugs are capability-based; no slug equals bot id `luna`.
- **Current spec state:** specs.yml + core-registry.yml + connectors-registry.yml; requirement and asset ids as above; traceability and validation.tests present.
- **Candidate assets:** Registry assets only; one optional candidate HealthServer (env) noted for future consideration.
- **Candidate requirements:** All current requirements from both registries; no new ids proposed.

This output is structured for the orchestrator to feed steps 2.1–2.4 (update specs index, update registries, categorize features to domains, sync docs).
