# Nova-Spec Scan Result — Vinekeepers

**Status:** Pass

**Scan inputs:** Full repository; project config from `.cursor/project.yml`: specs_index `specs/specs.yml`, docs_dir `mkdoc`, scan.source_dirs `[src/main/java, src/test/java]`, scan.root_config_files `[pom.xml, README.md, .env.example]`, scan.bots_config `config/bots.yaml`.

**Bot ids (excluded from feature slugs):** `luna`, `arrietty` (from config/bots.yaml).

---

## 1. Candidate features

Candidate features derived from registries and structure; slug ≠ bot id; suggested_domain set per REQ prefix and package (connectors → connectors, else domain/core).

| slug | title | requirement_ids | asset_ids (key) | suggested_domain |
|------|--------|-----------------|------------------|------------------|
| core | Specs governance and bootstrap | REQ-CORE-001, REQ-CORE-002 | ASSET-SPEC-INDEX, ASSET-REGISTRY, ASSET-APP, ASSET-BOOTSTRAP, ASSET-PACKAGE-MARKER | core |
| engine | Event-driven engine orchestration | REQ-CORE-003 | ASSET-ENGINE, ASSET-WORKFLOW-RUNNER, ASSET-TOOL-RUNNER, ASSET-REASONER-*, ASSET-OUTBOUND-RESPONSE, ASSET-APP-REPLY-SINK, ASSET-REPLY-TARGET, ASSET-CAPABILITIES, ASSET-RESPONSE-INTENT, ASSET-REPLY-SENDER, ASSET-REPLY-TARGET-RESOLVER, ASSET-DISCORD-REPLY-TARGET-RESOLVER | core |
| env | Environment and .env loading | REQ-ENV-001 | ASSET-ENV-LOADER, ASSET-ENV, ASSET-APP | env |
| config | Bot config from YAML | REQ-CONFIG-001 | ASSET-CONFIG-LOADER, ASSET-BOT-CONFIG, ASSET-BOTS-YAML, ASSET-BOT-DEFINITION | config |
| routing | Event routing and normalized context | REQ-BOT-001 | ASSET-ROUTER, ASSET-ROUTING, ASSET-EVENT-FILTER, ASSET-ROUTING-FILTER, ASSET-NORMALIZED-EVENT-CONTEXT | bot |
| bot | Bot runtime definition and tool policy | REQ-BOT-002, REQ-BOT-003 | ASSET-TOOL-POLICY, ASSET-CONNECTOR-IDENTITY, ASSET-BOT-DEFINITION, ASSET-CONVERSATION-MODE, ASSET-PERSONA, ASSET-MODEL-PROFILE, ASSET-MEMORY-POLICY | bot |
| events | Event bus and publish/subscribe | REQ-EVENTS-001 | ASSET-EVENT-BUS, ASSET-EVENT, ASSET-EVENT-SOURCE, ASSET-EVENT-SUBSCRIBER | events |
| state | State store for workflow state | REQ-STATE-001 | ASSET-STATE-STORE, ASSET-LUNA-STATE | state |
| tools | Tool registry and execution | REQ-TOOLS-001 | ASSET-TOOL, ASSET-TOOL-REGISTRY, ASSET-TOOL-RUNNER, ASSET-CURSOR-FULL-RUN-TOOL, ASSET-ECHO-TOOL, ASSET-STUB-TOOL, ASSET-BOOTSTRAP | tools |
| audit | Audit log for tool calls and outcomes | REQ-AUDIT-001 | ASSET-AUDIT-LOG, ASSET-AUDIT-RECORDER | audit |
| reasoner | Reasoner interface for bot decisions | REQ-REASONER-001 | ASSET-REASONER, ASSET-REASONER-INPUT, ASSET-REASONER-OUTPUT, ASSET-PROPOSED-TOOL-CALL, ASSET-STUB-REASONER, ASSET-ENGINE | reasoner |
| discord | Discord event source and reply | REQ-CONNECTORS-DISCORD-001 | ASSET-REPLY-SENDER, ASSET-OUTBOUND-GATEWAY, ASSET-CONNECTOR-*, ASSET-DISCORD-*, ASSET-OUTBOUND-DELIVERY-ROUTER, ASSET-SPACE-OPERATIONS*, ASSET-CREATE-ROOM/THREAD-*, ASSET-REPLY-TARGET-RESOLVER, ASSET-DISCORD-REPLY-TARGET-RESOLVER | connectors |
| github | GitHub event source | REQ-CONNECTORS-GITHUB-001 | ASSET-GITHUB-SOURCE | connectors |
| workflow | Workflow runners and session lifecycle | REQ-WORKFLOW-001 | ASSET-WORKFLOW, ASSET-WORKFLOW-RESULT, ASSET-STUB-*, ASSET-WORKFLOW-RUNNER, ASSET-WORKFLOW-RUN-RESULT, ASSET-WORKFLOW-RUNNER-FACTORY, ASSET-SESSION-KEY-*, ASSET-CONFIGURABLE-WORKFLOW-*, ASSET-DYNAMIC-CHOICE-PROVIDER-REGISTRY | workflow |
| workflow-steps | Workflow step DSL and branching actions | REQ-WORKFLOW-001 | ASSET-STEP-RESULT, ASSET-STEP-OUTCOME, ASSET-WORKFLOW-STEP, ASSET-WORKFLOW-ACTION, ASSET-WORKFLOW-ACTION-REGISTRY, ASSET-WORKFLOW-DEFINITION, ASSET-*-STEP, ASSET-WORKFLOW-TRANSFORMS, ASSET-WORKFLOW-CONDITION-EVALUATOR, ASSET-WORKFLOW-CAPABILITY-SUPPORT | workflow |
| cursor-gathering | Cursor-backed gathering workflow | REQ-LUNA-001 | ASSET-LUNA-STATE, ASSET-LUNA-WORKFLOW, ASSET-CURSOR-*, ASSET-LIFECYCLE-*, ASSET-RUNTIME-BOT-INSTANCE, ASSET-CREATE-CHANNEL-ACTION, ASSET-POST-CHANNEL-MESSAGE-ACTION, ASSET-PROVISION-BOT-INSTANCE-ACTION, ASSET-CREATE-LIFECYCLE-CONTEXT-ACTION, ASSET-CREATE-THREAD-ACTION, ASSET-LAUNCH-CURSOR-RUN-ACTION, ASSET-GITHUB-REPOS-CHOICE-PROVIDER, ASSET-BOTS-YAML, ASSET-BOOTSTRAP, ASSET-ENGINE, ASSET-SPACE-OPERATIONS*, ASSET-REPLY-SENDER, ASSET-OUTBOUND-GATEWAY | workflow |

Note: No feature slug equals a bot id (luna, arrietty). Luna/Arrietty capabilities are folded into config, workflow, and cursor-gathering.

---

## 2. Current spec state

| Registry file | Exists | Requirements | Assets | Features |
|---------------|--------|--------------|--------|----------|
| specs/specs.yml | yes | — (index) | — | — |
| specs/core-registry.yml | yes | 3 | 15 | 2 (core, engine) |
| specs/connectors-registry.yml | yes | 2 | 26 | 2 (discord, github) |
| specs/env-registry.yml | yes | 1 | 3 | 1 (env) |
| specs/config-registry.yml | yes | 1 | 4 | 1 (config) |
| specs/bot-registry.yml | yes | 3 | 11 | 2 (routing, bot) |
| specs/events-registry.yml | yes | 1 | 4 | 1 (events) |
| specs/state-registry.yml | yes | 1 | 2 | 1 (state) |
| specs/tools-registry.yml | yes | 1 | 7 | 1 (tools) |
| specs/audit-registry.yml | yes | 1 | 2 | 1 (audit) |
| specs/reasoner-registry.yml | yes | 1 | 6 | 1 (reasoner) |
| specs/workflow-registry.yml | yes | 2 | 50+ | 3 (workflow, workflow-steps, cursor-gathering) |

**Spec index:** `specs/specs.yml` — scope.primary_assets, change_triggers.paths, specs[].file (11 registries), domains (11), interfaces.cli, validation.commands.

**Schemas:** `specs/schema/specs-index.schema.json`, `specs/schema/req-registry.schema.json`.

---

## 3. Enumerated paths (summary)

- **Source:** `src/main/java` — 133 .java files; `src/test/java` — 56 .java files. Packages: com.vinekeepers (app), core, config, env, events, state, bot, tools, audit, reasoner, workflow (+ steps, actions), connectors, core.cursor, interactions, providers, util.
- **Specs:** `specs/**/*.yml` (12 files including specs.yml), `specs/schema/**/*.json` (2).
- **Cursor:** `.cursor/rules/**`, `.cursor/skills/**`, `.cursor/workflows/**`.
- **Root/config:** pom.xml, README.md, .env.example; config/bots.yaml (scan.bots_config).

---

## 4. Structure (derived)

- **Entrypoint:** `com.vinekeepers.VinekeepersApp` (main); loads .env via EnvLoader, creates Bootstrap; Bootstrap builds EventBus, StateStore, Router, Engine, ConnectorRegistry, Discord adapter, registers engine reply sender/sink/ReplyTargetResolver by connector id, workflow runners per bot.
- **Test → production:** Test classes map 1:1 to production (e.g. EnvLoaderTest→EnvLoader, RouterTest→Router, ConfigurableWorkflowRunnerTest→ConfigurableWorkflowRunner, DiscordEventSourceTest→DiscordEventSource, VinekeepersEngineTest→VinekeepersEngine).
- **Spec roles:** specs.yml = index; core-registry.yml … workflow-registry.yml = registries; schema/*.json = index and req-registry schemas.

---

## 5. Candidate assets and requirements (handoff)

**Candidate assets:** All assets already listed in the 11 registries; no net-new asset list. Key themes: entrypoint (ASSET-APP, ASSET-BOOTSTRAP), engine and reply model (ASSET-ENGINE, ASSET-OUTBOUND-RESPONSE, ASSET-APP-REPLY-SINK, ASSET-REPLY-TARGET, ASSET-CAPABILITIES, ASSET-RESPONSE-INTENT), env (ASSET-ENV-LOADER, ASSET-ENV), config (ASSET-CONFIG-LOADER, ASSET-BOTS-YAML, ASSET-BOT-DEFINITION), routing (ASSET-ROUTER, ASSET-ROUTING-FILTER, ASSET-NORMALIZED-EVENT-CONTEXT), connectors (Discord/GitHub sources, ReplySender, OutboundGateway, SpaceOperations, ReplyTargetResolver), workflow (WorkflowRunner, ConfigurableWorkflowRunner, steps, actions), state (StateStore, LifecycleContext, LifecycleContextStore), tools (ToolRunner, CursorFullRunTool), reasoner (Reasoner, ReasonerInput/Output), audit (AuditLog, AuditRecorder), cursor (CursorCloudAdapter, CursorCloudRunMonitor, CursorInstructionComposer, LifecycleRunRecord).

**Candidate requirements:** All requirements already in registries (REQ-CORE-001/002/003, REQ-ENV-001, REQ-CONFIG-001, REQ-BOT-001/002/003, REQ-EVENTS-001, REQ-STATE-001, REQ-TOOLS-001, REQ-AUDIT-001, REQ-REASONER-001, REQ-CONNECTORS-DISCORD-001, REQ-CONNECTORS-GITHUB-001, REQ-WORKFLOW-001, REQ-LUNA-001). Themes: spec bootstrap, app bootstrap, event-driven engine, .env load, YAML bot config, route events to bots, tool policy, bot definition/persona, event bus, state store, tool registry/execution, audit log, reasoner interface, Discord connector, GitHub connector, workflow/runner/DSL, Luna/Cursor gathering and lifecycle room.

---

**Next phase:** infer-domains, categorize, update-registry (align feature slugs and suggested_domain with domains in specs.yml; fix any drift in asset/requirement references across registries).
