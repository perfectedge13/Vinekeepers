# Change context (for plan_change / implement)

## Scope

**Request-derived:** Implement Luna bot feature. Luna listens on Discord for `/Luna`, runs a multi-turn conversation to gather (1) which project to update and (2) what code change to make, then invokes Cursor Cloud API to create feature branch, run nova-code/nova-commit, push to GitHub, optionally nova-pr.

**Features in scope:** FEAT-CORE, FEAT-BOT, FEAT-CONFIG, FEAT-EVENTS, FEAT-STATE, FEAT-WORKFLOW, FEAT-CONNECTORS-DISCORD (existing); **Luna** (new feature to add).

**Requirements in scope:** REQ-CORE-002, REQ-CORE-003, REQ-CONFIG-001, REQ-BOT-001, REQ-BOT-002, REQ-BOT-003, REQ-EVENTS-001, REQ-STATE-001, REQ-WORKFLOW-001, REQ-CONNECTORS-DISCORD-001; plus **new Luna requirements** (to be added in core-registry and optionally connectors-registry).

**Assets in scope:** ASSET-APP, ASSET-BOOTSTRAP, ASSET-ENGINE, ASSET-CONFIG-LOADER, ASSET-BOT-CONFIG, ASSET-ROUTER, ASSET-ROUTING, ASSET-EVENT-FILTER, ASSET-ROUTING-FILTER, ASSET-BOT-DEFINITION, ASSET-STATE-STORE, ASSET-WORKFLOW, ASSET-WORKFLOW-RESULT, ASSET-EVENT-BUS, ASSET-EVENT, ASSET-DISCORD-SOURCE; **new:** GatheringState (ex-LunaConversationState), CursorCloudGatheringWorkflow (ex-LunaGatheringWorkflow), Cursor Cloud API adapter, Discord reply/send path (connector), config/routing for bot id `luna` and trigger `/Luna`.

---

## Per feature

### FEAT-CORE (core engine, bootstrap)

**Feature:** title: Core engine, bootstrap, and specs; status: active; doc_path: (default) mkdoc/features/domain/core/core.md; summary: Specs bootstrap, application bootstrap, event-driven engine. Assets: ASSET-APP, ASSET-BOOTSTRAP, ASSET-ENGINE, ASSET-PACKAGE-MARKER.

**Requirements:** REQ-CORE-002 — Application bootstrap and entrypoint. Statement: VinekeepersApp is the entrypoint; loads .env and bootstraps engine, config, connectors. Criteria: Main class runs Bootstrap; Bootstrap wires engine, config loader, event sources. REQ-CORE-003 — Event-driven engine routes events to bots. Statement: VinekeepersEngine receives events, routes to matching bots, runs core loop (load state, reason, act, persist, respond). Criteria: Engine subscribes to event bus and dispatches by routing rules; no bot for id logs warning. Tests: UNIT-VINEKEEPERS-APP, UNIT-ENGINE. Anti_patterns: (none in registry).

**Assets:** ASSET-BOOTSTRAP (path: src/main/java/com/vinekeepers/core/Bootstrap.java, role: Wire engine, config, connectors), ASSET-ENGINE (path: src/main/java/com/vinekeepers/core/VinekeepersEngine.java, role: Route events to bots; run core loop).

**Doc excerpts:**  
**Decisions:** not present.  
**Contracts:** No external REST APIs. Tool invocations (e.g. discord.reply, github.comment) are per tool, executed by ToolRunner with BotContext. EventSource: start(EventBus). EventSubscriber: onEvent(Event). Workflow, StateStore, AuditLog interfaces.  
**Known-issues:** (check core/known-issues.md — excerpt not present in read).

**Implement note:** Engine must wire Luna's replies back to Discord (reply path). Core how-it-works: "respond (e.g. Discord reply) if applicable"; tool side effects include Discord message.

---

### FEAT-BOT (routing, bot definition)

**Feature:** title: Bot definition, routing, and tool policy; status: active; doc_path: mkdoc/features/domain/core/bot.md; summary: Router, Routing, EventFilter, ToolPolicy, BotDefinition, Persona, ModelProfile, MemoryPolicy.

**Requirements:** REQ-BOT-001 — Route events to bots by routing rules. Statement: Router matches events using Routing and EventFilter/RoutingFilter. Criteria: Router.match(event) returns matching BotDefinitions; filters apply Discord/GitHub criteria. REQ-BOT-002 — Tool policy allow/deny and approval. REQ-BOT-003 — BotDefinition composes Persona, ModelProfile, Workflow, ToolPolicy, Routing, MemoryPolicy; YAML config. Tests: UNIT-ROUTER, UNIT-TOOL-POLICY, MANUAL-BOT-DEF. Anti_patterns: (none in registry).

**Assets:** ASSET-ROUTER, ASSET-ROUTING, ASSET-EVENT-FILTER, ASSET-ROUTING-FILTER, ASSET-BOT-DEFINITION, ASSET-TOOL-POLICY, ASSET-PERSONA, ASSET-MODEL-PROFILE, ASSET-MEMORY-POLICY.

**Doc excerpts:** **Contracts:** Router.match(Event) → List&lt;BotDefinition&gt;. BotDefinition: botId, persona, model, workflow, toolPolicy, routing, memoryPolicy. Routing: discord/git filters.

**Implement note:** Add routing config for bot id `luna` and trigger `/Luna` (e.g. Discord command or message filter).

---

### FEAT-CONFIG

**Feature:** title: Bot config from YAML; status: active; doc_path: mkdoc/features/domain/core/config.md. Summary: ConfigLoader, BotConfig.

**Requirements:** REQ-CONFIG-001 — Load bot definitions from YAML; BotConfig models structure. Criteria: ConfigLoader loads YAML and returns bot configs; BotConfig maps to BotDefinition. Tests: UNIT-CONFIG-LOADER. Anti_patterns: (none).

**Assets:** ASSET-CONFIG-LOADER, ASSET-BOT-CONFIG.

**Doc excerpts:** **Contracts:** YAML: botId, persona, model, workflow, stateSchema, tools, routing (discord, git filters), memory. ConfigLoader: load → BotConfig.

**Implement note:** Config and routing for bot id `luna`, trigger `/Luna`; ensure BotConfig supports command/trigger fields if not already present.

---

### FEAT-EVENTS

**Feature:** title: Event bus and publish/subscribe; status: active. Summary: EventBus, Event, EventSource, EventSubscriber.

**Requirements:** REQ-EVENTS-001 — EventBus publish/subscribe; Event has sourceId, kind, payload. Criteria: EventBus.publish and subscribe work; Event carries sourceId, kind, payload. Tests: UNIT-EVENT-BUS. Anti_patterns: (none).

**Assets:** ASSET-EVENT-BUS, ASSET-EVENT, ASSET-EVENT-SOURCE, ASSET-EVENT-SUBSCRIBER.

**Doc excerpts:** (decisions/contracts/known-issues — use Event contract from core: sourceId, kind, payload.)

---

### FEAT-STATE

**Feature:** title: State store for workflow state; status: active. Summary: StateStore persists/loads per-bot, per-conversation state.

**Requirements:** REQ-STATE-001 — StateStore load/save for bot and conversationKey. Criteria: load(botId, conversationKey) returns state or empty; save persists. Tests: UNIT-STATE-STORE. Anti_patterns: (none).

**Assets:** ASSET-STATE-STORE.

**Doc excerpts:** **Contracts:** StateStore: load(botId, key), save(botId, key, state). State: JSON-shaped per workflow. Key: (botId, conversationKey).

**Implement note:** GatheringState will be persisted via StateStore for Luna's conversationKey (e.g. Discord channel or user id).

---

### FEAT-WORKFLOW

**Feature:** title: Workflow state machine; status: active. Summary: Workflow&lt;S&gt; handle → WorkflowResult&lt;S&gt;; StubWorkflow, StubState.

**Requirements:** REQ-WORKFLOW-001 — Workflow interface and WorkflowResult; state machine per bot. Criteria: Workflow interface and WorkflowResult exist; StubWorkflow/StubState when no custom workflow. Tests: MANUAL-WORKFLOW. Anti_patterns: (none).

**Assets:** ASSET-WORKFLOW, ASSET-WORKFLOW-RESULT, ASSET-STUB-WORKFLOW, ASSET-STUB-STATE.

**Doc excerpts:** **Contracts:** Workflow&lt;S&gt;: handle(Event, BotContext, S) → WorkflowResult&lt;S&gt;. WorkflowResult&lt;S&gt;: nextState, actions, done flag.

**Implement note:** CursorCloudGatheringWorkflow implements Workflow&lt;GatheringState&gt;; steps: gather project, gather code change, then invoke Cursor Cloud API (feature branch, nova-code/nova-commit, push, optional nova-pr).

---

### FEAT-CONNECTORS-DISCORD

**Feature:** title: Discord event source; status: active; doc_path: mkdoc/features/domain/connectors/discord.md. Summary: DiscordEventSource implements EventSource; translates Discord payloads to internal events.

**Requirements:** REQ-CONNECTORS-DISCORD-001 — DiscordEventSource implements EventSource; emits events to bus when started. Criteria: Exists and implements EventSource; connector emits events onto event bus. Tests: MANUAL-CONNECTORS-DISCORD. Anti_patterns: (none in registry).

**Assets:** ASSET-DISCORD-SOURCE (path: src/main/java/com/vinekeepers/connectors/DiscordEventSource.java, role: Discord event source).

**Doc excerpts:** **Decisions:** (No decisions recorded.) **Contracts:** EventSource.start(EventBus). Event: sourceId, kind, payload. **Known-issues:** None.

**Implement note:** Add **Discord receive + reply path**: receive is existing (events onto bus); **reply path** — connector must be able to send messages back to Discord (e.g. reply to /Luna or in channel). Engine or tool invokes connector send/reply with channel/message id from event payload. Document new contract (e.g. DiscordReply or sendMessage) in connectors-registry and mkdoc.

---

### Luna (new feature)

**Feature:** (to be added) id: FEAT-LUNA or similar; slug: luna; title: Luna bot — Discord /Luna, multi-turn gather, Cursor Cloud API; status: draft/active; doc_path: mkdoc/features/domain/core/luna.md (or connectors if preferred). Summary: Luna listens for /Luna on Discord, gathers project and code change via multi-turn conversation, then calls Cursor Cloud API for feature branch, nova-code/nova-commit, push, optional nova-pr.

**Requirements (to add):**  
- Luna trigger and routing: Bot id `luna` registered; trigger /Luna (Discord slash command or message).  
- Luna conversation state: GatheringState holds project, code change description, and step (e.g. awaiting_project, awaiting_change, ready_to_run).  
- Luna gathering workflow: CursorCloudGatheringWorkflow implements Workflow&lt;GatheringState&gt;; multi-turn to fill project and change; then invoke Cursor adapter.  
- Cursor Cloud API adapter: Create feature branch, run nova-code/nova-commit, push to GitHub, optionally nova-pr; adapter in core (or dedicated package).  
- Engine wiring: Luna's replies (workflow output messages) delivered to Discord via connector reply path.  
- Config/routing: bots.luna with trigger /Luna and routing so only /Luna events match.

**Assets (to add):**  
- GatheringState (e.g. src/main/java/com/vinekeepers/workflow/GatheringState.java or state/).  
- CursorCloudGatheringWorkflow (e.g. src/main/java/com/vinekeepers/workflow/CursorCloudGatheringWorkflow.java).  
- Cursor Cloud API adapter (e.g. src/main/java/com/vinekeepers/core/cursor/ or adapters/).  
- Discord reply/send in connector (extend ASSET-DISCORD-SOURCE or new asset for send path).  
- Config: luna bot entry in YAML with id luna, trigger /Luna, workflow CursorCloudGatheringWorkflow, stateSchema GatheringState.

**Anti_patterns:** Avoid: hardcoding Discord channel in workflow; skipping tool policy for Cursor API calls; storing secrets in state. Use BotContext for channel/message ids; enforce ToolPolicy for Cursor adapter if exposed as tool.

**Doc excerpts:** New feature doc and sub-pages (decisions, contracts, known-issues) to be created under mkdoc/features/domain/core/luna.md and luna/*.md. Contracts: GatheringState schema; CursorCloudGatheringWorkflow steps; Cursor Cloud API adapter interface (createBranch, runNovaCommit, push, createPr); Discord reply contract (channelId, messageId, content).

---

## Schema constraints

- **req-registry:** requirements use id, title, statement, status, priority, type, behavior, acceptance.criteria, traceability.assets, validation.tests; optional anti_patterns. assets use id, kind, path, role, requires (requirement ids); optional anti_patterns. Do not add new top-level keys; stay within schema.
- **specs index:** specs[].file, scope.primary_assets, change_triggers.paths; no new keys.

---

## Summary for implement

1. **Connectors (Discord):** Implement reply/send path so engine or tools can send messages to Discord (channel/message from event payload). Keep EventSource receive path; add send/reply API used by engine when Luna responds.
2. **Core (engine, bootstrap):** Wire Luna bot; ensure when Luna's workflow produces a reply, engine passes it to Discord connector (reply path). No change to Bootstrap unless new connector or adapter registration needed.
3. **Core (workflow, state):** Add GatheringState (project, codeChangeDescription, step). Add CursorCloudGatheringWorkflow implementing Workflow&lt;GatheringState&gt;; steps: ask project → ask change → call Cursor adapter.
4. **Core (Cursor adapter):** Implement adapter for Cursor Cloud API: create feature branch, run nova-code/nova-commit, push to GitHub, optional nova-pr. Use env/config for API keys; do not hardcode.
5. **Config/routing:** Add bot id `luna` in YAML; routing filter for trigger `/Luna` (Discord); reference CursorCloudGatheringWorkflow and GatheringState.
6. **Specs:** Update core-registry.yml with new requirements and assets for Luna; update connectors-registry.yml if new Discord reply asset or requirement. Add mkdoc for Luna (feature page, decisions, contracts, known-issues).
