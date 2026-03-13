# Nova-spec scan phase — output

**Status:** Pass  
**Context:** Vinekeepers repo; spec index `specs/specs.yml`; registries `specs/core-registry.yml`, `specs/connectors-registry.yml`; project config `.cursor/project.yml`.

---

## 1. Enumerated paths

### Source (from `scan.source_dirs`)
- **src/main/java:** 109 `.java` files under `com.vinekeepers` (app, core, env, config, bot, events, state, tools, audit, reasoner, workflow, workflow.steps, workflow.actions, interactions, connectors, core.cursor, providers, util).
- **src/test/java:** 42 `.java` test files mirroring production packages.

### Specs (from `paths.specs_index` = `specs/specs.yml`)
- **Spec index:** `specs/specs.yml`
- **Registry files:** `specs/core-registry.yml`, `specs/connectors-registry.yml`
- **Schema:** `specs/schema/specs-index.schema.json`, `specs/schema/req-registry.schema.json`

### Cursor
- **.cursor/rules:** 5 files (guardrails.mdc, vinekeepers-standards.mdc, spec-workflow-core.mdc, spec-workflow.mdc, spec-workflow-vinekeepers.mdc)
- **.cursor/skills:** 64 files (nova-spec, nova-code, nova-commit, nova-pr, nova-wiki, nova-grafana, common, vinekeepers-code)
- **.cursor/workflows:** 19 files (nova-spec.yml, nova-code.yml, nova-commit.yml, nova-pr.yml, nova-wiki.yml, reports, plan_change_*.md)

### Root/config (from `scan.root_config_files` + `scan.bots_config`)
- **Root:** `pom.xml`, `README.md`, `.env.example`
- **Config:** `config/bots.yaml` (bots: luna, arrietty; routing; workflows DSL)

---

## 2. Derived structure

### Packages and entrypoints
- **Entrypoint:** `com.vinekeepers.VinekeepersApp` (public static void main); loads `.env` via EnvLoader, creates Bootstrap, starts engine and connectors.
- **Packages:** core (Bootstrap, VinekeepersEngine, cursor/*), env (EnvLoader, Env, HealthServer), config (ConfigLoader, BotConfig), bot (Router, BotDefinition, ToolPolicy, …), events (EventBus, Event, EventSource, EventSubscriber), state (StateStore, LifecycleContext, LifecycleContextStore), tools (Tool, ToolRegistry, ToolRunner, …), audit (AuditLog, AuditRecorder), reasoner (Reasoner, ReasonerInput, ReasonerOutput, …), workflow (Workflow, WorkflowRunner, steps, actions), interactions (OutboundResponse, AppReplySink, ReplyTarget, ResponseIntent, …), connectors (DiscordEventSource, JdaDiscordGateway, DiscordAppReplySink, GitHubEventSource, …), providers (GitHubReposChoiceProvider), util (PackageMarker).

### Test → production mapping (representative)
| Test class | Production target |
|------------|-------------------|
| EnvLoaderTest | EnvLoader |
| VinekeepersAppTest | VinekeepersApp |
| ConfigLoaderTest | ConfigLoader |
| RouterTest | Router |
| ToolPolicyTest | ToolPolicy |
| EventBusTest | EventBus |
| StateStoreTest | StateStore |
| ToolRunnerTest | ToolRunner |
| VinekeepersEngineTest | VinekeepersEngine |
| ConfigurableWorkflowRunnerTest | ConfigurableWorkflowRunner |
| WorkflowRunnerFactoryTest | WorkflowRunnerFactory |
| DiscordEventSourceTest | DiscordEventSource |
| DiscordAppReplySinkTest | DiscordAppReplySink |
| OutboundDeliveryRouterTest | OutboundDeliveryRouter |
| CursorCloudAdapterImplTest | CursorCloudAdapterImpl |
| … (42 test files total) | Corresponding main classes |

### Key config/spec roles
- **Spec index:** `specs/specs.yml` — primary_assets, change_triggers, specs[], domains[], interfaces, validation.
- **Registries:** `specs/core-registry.yml` (core/env/config/bot/events/state/tools/audit/reasoner/workflow/Luna), `specs/connectors-registry.yml` (Discord, GitHub).
- **Schemas:** specs-index.schema.json (index), req-registry.schema.json (registries).

---

## 3. Candidate assets and candidate requirements (handoff)

### Candidate assets (from scan; existing registry coverage)
All significant main sources and configs are already represented in the two registries (78 assets in core-registry, 7 in connectors-registry). Paths and roles align with `assets[].path` and `assets[].role`.

- **Potential gap:** `src/main/java/com/vinekeepers/env/HealthServer.java` — not currently an asset; optional health/server concern. Suggested role: health check server; theme: env or ops. Can be added as optional asset or left out.

### Candidate requirements (from scan; existing registry coverage)
Requirements inferred from code/README/structure match the existing registry set:

- **Core:** REQ-CORE-001 (specs bootstrap), REQ-CORE-002 (application bootstrap), REQ-CORE-003 (event-driven engine).
- **Env:** REQ-ENV-001 (load .env at startup).
- **Config:** REQ-CONFIG-001 (load bot config from YAML).
- **Bot:** REQ-BOT-001 (route events by routing rules and ownership), REQ-BOT-002 (tool policy), REQ-BOT-003 (bot definition and persona).
- **Events:** REQ-EVENTS-001 (event bus publish/subscribe).
- **State:** REQ-STATE-001 (state store for workflow state).
- **Tools:** REQ-TOOLS-001 (tool registry and execution).
- **Audit:** REQ-AUDIT-001 (audit log).
- **Reasoner:** REQ-REASONER-001 (reasoner interface).
- **Workflow:** REQ-WORKFLOW-001 (workflow state machine and config-driven runners).
- **Luna/Cursor gathering:** REQ-LUNA-001 (Luna bot, Discord mention, multi-turn gather, Cursor Cloud API, lifecycle room).
- **Connectors:** REQ-CONNECTORS-DISCORD-001 (Discord event source and reply), REQ-CONNECTORS-GITHUB-001 (GitHub event source).

No new requirement themes identified; candidate list = current registry requirements.

---

## 4. Current spec state summary

### specs/specs.yml
- **scope.primary_assets:** VinekeepersApp, VinekeepersEngine, Bootstrap, EnvLoader, ConfigLoader.
- **change_triggers.paths:** src, config, specs, pom.xml, README.md, mkdoc, mkdocs.yml, AGENTS.md, .cursor/project.yml, .cursor/rules, .cursor/skills, .cursor/workflows.
- **specs[]:** core-registry.yml, connectors-registry.yml.
- **domains[]:** core, env, config, bot, events, state, tools, audit, reasoner, workflow, connectors (each with spec_file).
- **interfaces.cli:** mvn test, mvn compile, npm run validate-specs, validate-drift, validate-docs.
- **validation:** test (mvn test), build_check (mvn compile), shell powershell, entrypoint VinekeepersApp.

### specs/core-registry.yml
- **Schema:** req-registry 1.0.0; enums (status, priority, type, asset_kind, dependency_*).
- **Dependencies:** DEP-SLF4J, DEP-LOGBACK, DEP-SNAKEYAML, DEP-JACKSON-DATABIND, DEP-JUNIT.
- **Assets:** 78 (ASSET-SPEC-INDEX through ASSET-LAUNCH-CURSOR-RUN-ACTION).
- **Requirements:** REQ-CORE-001, REQ-ENV-001, REQ-CORE-002, REQ-CORE-003, REQ-CONFIG-001, REQ-BOT-001, REQ-BOT-002, REQ-BOT-003, REQ-EVENTS-001, REQ-STATE-001, REQ-TOOLS-001, REQ-AUDIT-001, REQ-REASONER-001, REQ-WORKFLOW-001, REQ-LUNA-001.
- **Features:** FEAT-ENV, FEAT-CONFIG, FEAT-ROUTING, FEAT-BOT, FEAT-EVENTS, FEAT-STATE, FEAT-TOOLS, FEAT-AUDIT, FEAT-REASONER, FEAT-WORKFLOW, FEAT-WORKFLOW-STEPS, FEAT-CURSOR-GATHERING, FEAT-CORE, FEAT-ENGINE (all with requirement_ids, asset_ids, domain_slug, doc_path, status active).
- **Traceability:** requirements → assets and validation.tests; many tests reference testClass/testMethod.

### specs/connectors-registry.yml
- **Schema:** req-registry 1.0.0; same enums.
- **Dependencies:** DEP-JDA (used by REQ-CONNECTORS-DISCORD-001).
- **Assets:** 7 (ASSET-DISCORD-SOURCE, ASSET-DISCORD-REPLY, ASSET-DISCORD-GATEWAY, ASSET-DISCORD-GATEWAY-CONTRACT, ASSET-DISCORD-REPLY-SINK, ASSET-OUTBOUND-DELIVERY-ROUTER, ASSET-GITHUB-SOURCE).
- **Requirements:** REQ-CONNECTORS-DISCORD-001, REQ-CONNECTORS-GITHUB-001.
- **Features:** FEAT-CONNECTORS-DISCORD (slug: discord), FEAT-CONNECTORS-GITHUB (slug: github); domain_slug: connectors; doc_path, status active.
- **Traceability:** requirements → assets; validation.tests (unit + manual steps).

### Bot ids (from config/bots.yaml)
- **bots[].id:** `luna`, `arrietty`. No candidate feature slug equals a bot id (per scan rules).

---

## 5. Candidate features (slug, title, requirement_ids, asset_ids, suggested_domain)

Features are grouped by logical area with **suggested_domain**: **connectors** for REQ-CONNECTORS* and connector assets; **core** or the existing domain_slug for the rest. Finer-grained: Discord and GitHub are separate features (discord, github). Bot ids (luna, arrietty) are not used as feature slugs.

| slug | title | requirement_ids | asset_ids (key) | suggested_domain |
|------|--------|-----------------|------------------|-------------------|
| **core** | Specs governance and bootstrap | REQ-CORE-001, REQ-CORE-002 | ASSET-SPEC-INDEX, ASSET-REGISTRY, ASSET-APP, ASSET-BOOTSTRAP, ASSET-PACKAGE-MARKER | core |
| **engine** | Event-driven engine orchestration | REQ-CORE-003 | ASSET-ENGINE, ASSET-WORKFLOW-RUNNER, ASSET-TOOL-RUNNER, ASSET-OUTBOUND-RESPONSE, ASSET-APP-REPLY-SINK, ASSET-REPLY-TARGET, ASSET-CAPABILITIES, ASSET-RESPONSE-INTENT, … | core |
| **env** | Environment and .env loading | REQ-ENV-001 | ASSET-ENV-LOADER, ASSET-ENV, ASSET-APP | env → core |
| **config** | Bot config from YAML | REQ-CONFIG-001 | ASSET-CONFIG-LOADER, ASSET-BOT-CONFIG, ASSET-BOTS-YAML, ASSET-BOT-DEFINITION | config → core |
| **routing** | Event routing and normalized context | REQ-BOT-001 | ASSET-ROUTER, ASSET-ROUTING, ASSET-EVENT-FILTER, ASSET-ROUTING-FILTER, ASSET-NORMALIZED-EVENT-CONTEXT | bot → core |
| **bot** | Bot runtime definition and tool policy | REQ-BOT-002, REQ-BOT-003 | ASSET-TOOL-POLICY, ASSET-BOT-DEFINITION, ASSET-CONVERSATION-MODE, ASSET-PERSONA, ASSET-MODEL-PROFILE, ASSET-MEMORY-POLICY | bot → core |
| **events** | Event bus and publish/subscribe | REQ-EVENTS-001 | ASSET-EVENT-BUS, ASSET-EVENT, ASSET-EVENT-SOURCE, ASSET-EVENT-SUBSCRIBER | events → core |
| **state** | State store for workflow state | REQ-STATE-001 | ASSET-STATE-STORE, ASSET-LUNA-STATE | state → core |
| **tools** | Tool registry and execution | REQ-TOOLS-001 | ASSET-TOOL, ASSET-TOOL-REGISTRY, ASSET-TOOL-RUNNER, ASSET-CURSOR-FULL-RUN-TOOL, ASSET-ECHO-TOOL, ASSET-STUB-TOOL, ASSET-BOOTSTRAP | tools → core |
| **audit** | Audit log for tool calls and outcomes | REQ-AUDIT-001 | ASSET-AUDIT-LOG, ASSET-AUDIT-RECORDER | audit → core |
| **reasoner** | Reasoner interface for bot decisions | REQ-REASONER-001 | ASSET-REASONER, ASSET-REASONER-INPUT, ASSET-REASONER-OUTPUT, ASSET-PROPOSED-TOOL-CALL, ASSET-STUB-REASONER, ASSET-ENGINE | reasoner → core |
| **workflow** | Workflow runners and session lifecycle | REQ-WORKFLOW-001 | ASSET-WORKFLOW, ASSET-WORKFLOW-RUNNER, ASSET-WORKFLOW-RUN-RESULT, ASSET-WORKFLOW-RUNNER-FACTORY, ASSET-SESSION-KEY-STRATEGY, ASSET-SESSION-KEY-STRATEGIES, ASSET-CONFIGURABLE-WORKFLOW-STATE, ASSET-CONFIGURABLE-WORKFLOW-RUNNER, ASSET-DYNAMIC-CHOICE-PROVIDER-REGISTRY | workflow → core |
| **workflow-steps** | Workflow step DSL and branching actions | REQ-WORKFLOW-001 | ASSET-STEP-RESULT, ASSET-STEP-OUTCOME, ASSET-WORKFLOW-STEP, ASSET-WORKFLOW-ACTION, ASSET-WORKFLOW-ACTION-REGISTRY, ASSET-WORKFLOW-DEFINITION, ASSET-ASK-FOR-INPUT-STEP, ASSET-PROMPT-FOR-FIELD-STEP, ASSET-CAPTURE-FIELD-STEP, ASSET-CALL-ACTION-STEP, ASSET-BRANCH-STEP, ASSET-DONE-STEP, ASSET-DYNAMIC-CHOICE-PROVIDER, … | workflow → core |
| **cursor-gathering** | Cursor-backed gathering workflow | REQ-LUNA-001 | ASSET-LUNA-STATE, ASSET-LUNA-WORKFLOW, ASSET-CURSOR-ADAPTER, ASSET-CURSOR-ADAPTER-IMPL, ASSET-LUNA-RUN-STATE, ASSET-CURSOR-RUN-MONITOR, ASSET-CURSOR-INSTRUCTION-COMPOSER, ASSET-LIFECYCLE-CONTEXT, ASSET-LIFECYCLE-CONTEXT-STORE, ASSET-RUNTIME-BOT-INSTANCE, ASSET-CREATE-CHANNEL-ACTION, ASSET-POST-CHANNEL-MESSAGE-ACTION, ASSET-PROVISION-BOT-INSTANCE-ACTION, ASSET-CREATE-LIFECYCLE-CONTEXT-ACTION, ASSET-LAUNCH-CURSOR-RUN-ACTION, … | workflow → core |
| **discord** | Discord event source and reply | REQ-CONNECTORS-DISCORD-001 | ASSET-DISCORD-SOURCE, ASSET-DISCORD-REPLY, ASSET-DISCORD-GATEWAY, ASSET-DISCORD-GATEWAY-CONTRACT, ASSET-DISCORD-REPLY-SINK, ASSET-OUTBOUND-DELIVERY-ROUTER | **connectors** |
| **github** | GitHub event source | REQ-CONNECTORS-GITHUB-001 | ASSET-GITHUB-SOURCE | **connectors** |

(Full asset_id lists per feature are in the registry files; above table summarizes.)

---

## 6. Gaps

1. **Asset:** `src/main/java/com/vinekeepers/env/HealthServer.java` is not listed in any registry. Optional; add as ASSET-ENV-HEALTH-SERVER under FEAT-ENV if health checks are to be traced, or leave as implementation detail.
2. **Traceability:** All other main sources and configs are covered; test→production mapping and validation.tests align with the 42 test classes.
3. **Bot ids:** Correctly not used as feature slugs (luna, arrietty only in config; capability features cursor-gathering, bot, config, routing cover them).
4. **Domains:** suggested_domain is **connectors** only for discord and github; all other features use core or the existing domain slug that maps into core-registry.

---

**End of scan phase output.**
