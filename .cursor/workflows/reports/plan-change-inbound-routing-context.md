# Change context (for plan_change / implement)

## Scope

**Request-derived:** Implement inbound event routing architecture with contextual/ownership-based routing so configured bots (e.g. Arrietty) respond to inbound room events in rooms they own. Precedence: owned lifecycle room wins by default (single-owner). Bot-level "handles owned spaces" capability; router adds owner only when configured. Arrietty needs a meaningful (non-stub) workflow for room events (status, retry, close, simple replies). Apply to all inbound room events: messages, button interactions, select menus, modal submits. Preserve outbound delivery router, lifecycle context, no hardcoded Arrietty in Java.

**Features:** FEAT-ROUTING, FEAT-CONFIG, FEAT-BOT, FEAT-ENGINE, FEAT-CURSOR-GATHERING, FEAT-WORKFLOW.

**Requirements:** REQ-BOT-001, REQ-CONFIG-001, REQ-BOT-003, REQ-CORE-003, REQ-LUNA-001, REQ-WORKFLOW-001.

**Assets:** ASSET-ROUTER, ASSET-ROUTING, ASSET-ROUTING-FILTER, ASSET-NORMALIZED-EVENT-CONTEXT, ASSET-CONFIG-LOADER, ASSET-BOT-DEFINITION, ASSET-BOTS-YAML, ASSET-ENGINE, ASSET-LIFECYCLE-CONTEXT, ASSET-LIFECYCLE-CONTEXT-STORE; workflow for room events (new workflow ref, e.g. arrietty_room); ASSET-WORKFLOW-RUNNER-FACTORY, ASSET-CONFIGURABLE-WORKFLOW-RUNNER. OutboundDeliveryRouter and connector sink unchanged.

---

## Per feature / requirement

### FEAT-ROUTING (routing)

- **Feature:** Event routing and normalized context. doc_path: features/domain/bot/routing.md. status: active. summary: Router matches events to bots through routing filters over a normalized event view.
- **Requirements:** REQ-BOT-001 — Route events to bots by routing rules. Statement: Router matches incoming events to bots using Routing and EventFilter/RoutingFilter over a normalized event view; Discord trigger and mention apply only to message events; interaction events use author and channel only. Criteria: Router.match(event) returns list of matching BotDefinitions; filters apply Discord/GitHub criteria; normalized context exposes actorId, actorUsername; discordTrigger/discordMention only for message events; discordAuthors match by actorId or actorUsername. Anti_patterns: (none in spec).
- **Assets:** ASSET-ROUTER (path: src/main/java/com/vinekeepers/bot/Router.java, role: match events to bots by routing rules), ASSET-ROUTING, ASSET-ROUTING-FILTER, ASSET-NORMALIZED-EVENT-CONTEXT.
- **Doc excerpts (routing):**  
  **Decisions:** Discord interaction routing — discordTrigger/discordMention only for message events; for interactions use author, channel only. discordAuthors — optional list in YAML; NormalizedEventContext exposes actorId/actorUsername; match when author in list. Routing evaluated over normalized event view.  
  **Contracts:** Router returns bot ids whose routing rules match. NormalizedEventContext exposes actorId, actorUsername, channel, text, mentions; for kind interaction optional interactionId, token, customId, values.  
  **Known issues:** Available routing fields depend on connector payload; new predicates require filter code changes.

### FEAT-CONFIG (config)

- **Feature:** Bot config from YAML. doc_path: features/domain/config/config.md. status: active.
- **Requirements:** REQ-CONFIG-001 — Load bot config from YAML. Statement: ConfigLoader loads bot definitions, routing, workflows; workflow block, bot runtime options, optional discordTokenEnvKey, routing filter keys (discordTrigger, discordMention, discordAuthors) parsed from YAML. Criteria: ConfigLoader loads YAML and returns bot configs; BotConfig maps to BotDefinition; workflow.type/params and routing filters parsed; optional discordTokenEnvKey; step bind and content interpolation supported.
- **Assets:** ASSET-CONFIG-LOADER, ASSET-BOT-CONFIG, ASSET-BOTS-YAML, ASSET-BOT-DEFINITION.
- **Doc excerpts:** Config doc — workflow.type (stub, configured), workflowRef, routing filters, discordTokenEnvKey; workflows section defines DSL. New key: handlesOwnedSpaces (bot-level) to be parsed and placed on BotDefinition.

### FEAT-BOT (bot)

- **Feature:** Bot runtime definition and tool policy. doc_path: features/domain/bot/bot.md. status: active.
- **Requirements:** REQ-BOT-003 — Bot definition and persona/model. Statement: BotDefinition composes Persona, ModelProfile, workflowType/params, ToolPolicy, Routing, MemoryPolicy, optional discordTokenEnvKey; bots configured via YAML. Criteria: BotDefinition holds persona, model, workflowType, workflowParams, tool policy, routing, memory, conversation mode, optional session key strategy, optional discordTokenEnvKey.
- **Assets:** ASSET-BOT-DEFINITION (path: src/main/java/com/vinekeepers/bot/BotDefinition.java). Add optional handlesOwnedSpaces to definition; no hardcoded bot ids.

### FEAT-ENGINE (engine)

- **Feature:** Event-driven engine orchestration. doc_path: features/domain/core/engine.md. status: active.
- **Requirements:** REQ-CORE-003 — Event-driven engine routes events to bots. Statement: Engine receives events, routes to matching bots; for Discord message events adds bots with WAITING_INPUT for session key; runs WorkflowRunner, reasoner, applies patches and tool calls, delivers via sink; for Discord uses OutboundDeliveryRouter with lifecycle configuredBotId; no defer in engine. Criteria: Engine subscribes to event bus; for Discord message events adds waiting-session bots; uses registered runners; delivers via sink and lifecycle methods; OutboundDeliveryRouter for Discord; no silent fallback for lifecycle channel when bot sender unavailable.
- **Assets:** ASSET-ENGINE. Engine must pass LifecycleContextStore (and optionally bot handlesOwnedSpaces) into Router when calling route(), or Router must receive these dependencies so owner-based routing can run. No change to reply delivery path.
- **Doc excerpts (engine):** Decisions — Waiting-session routing for Discord (add bots with WAITING_INPUT for session key). Workflow runs before reasoner; ToolRunner executes proposed tools.

### FEAT-CURSOR-GATHERING (lifecycle room)

- **Feature:** Cursor-backed gathering workflow. doc_path: features/domain/workflow/cursor-gathering.md. status: active.
- **Requirements:** REQ-LUNA-001 — Luna bot, lifecycle room Phase 1. Statement: Luna registered with id luna; discordMention and optional discordAuthors; luna_cursor workflow; LifecycleRunRecord, LifecycleContext (channelId, configuredBotId, runtimeBotInstanceId, repo/requestText), LifecycleContextStore; create_channel, provision_bot_instance, create_lifecycle_context, post_channel_message, launch_cursor_run. Criteria: create_channel with lifecycleOwnerBotId and addPermissionOverride; create_lifecycle_context bind precedence; Arrietty template for lifecycle room instances.
- **Assets:** ASSET-LIFECYCLE-CONTEXT, ASSET-LIFECYCLE-CONTEXT-STORE. LifecycleContext has getChannelId(), getConfiguredBotId() — used to resolve "owner" bot for a channel. Store: getByChannelId(channelId) returns Optional<LifecycleContext>; context.getConfiguredBotId() is the owner bot id. Router adds this owner to matched bots only when that bot has handlesOwnedSpaces and event is in that channel (single-owner: owned lifecycle room wins).
- **Doc excerpts (cursor-gathering):** Contracts — LifecycleContext (channelId, configuredBotId, runtimeBotInstanceId, repo/requestText). LifecycleContextStore store/resolve by key. create_channel optional bind lifecycleOwnerBotId; addPermissionOverride for that bot. How-it-works — Provisioning sequence create_channel → … → launch_cursor_run; Arrietty template for per-channel instances.

### FEAT-WORKFLOW (workflow for room events)

- **Feature:** Workflow runners and session lifecycle. doc_path: features/domain/workflow/workflow.md. status: active.
- **Requirements:** REQ-WORKFLOW-001 — Workflow state machine and config-driven runners. Statement: WorkflowRunner runs workflow for event; WorkflowRunnerFactory creates by type (stub, configured); configurable workflows support prompt_for_field, capture_field, session key strategies. Criteria: WorkflowRunner.runResult(event, stateStore, botId); factory creates by type; stub/configured; configurable workflows with steps; session keys.
- **Assets:** New workflow definition in config (e.g. arrietty_room) with steps for room events: status, retry, close, simple replies; used by Arrietty via workflowRef in bots.yaml (arrietty currently has type: stub). No new Java workflow class named "Arrietty"; use configured workflow ref. ASSET-CONFIGURABLE-WORKFLOW-RUNNER, ASSET-WORKFLOW-RUNNER-FACTORY, ASSET-WORKFLOW-DEFINITION, step types (prompt_for_field, capture_field, call_action, branch, done). Room workflow may use call_action for status/retry/close if actions exist, or reply-only steps.
- **Anti_patterns (REQ-LUNA-001):** Hardcoding Discord channel in workflow. Storing secrets in state. Do not rely on discordTrigger alone for activation. No hardcoded Arrietty in Java — Arrietty is a bot id in config; workflow is referenced by workflowRef (e.g. arrietty_room).

---

## Schema constraints

- req-registry: requirement keys include id, title, statement, status, acceptance.criteria, traceability.assets, validation.tests; optional anti_patterns. Asset keys: id, kind, path, role, requires, feature_ids. No new requirement or asset keys; extend existing (e.g. BotDefinition with handlesOwnedSpaces in config/YAML and Java model).
- ConfigLoader/BotConfig: add optional handlesOwnedSpaces (boolean or flag) from YAML bot entry; BotDefinition holds it for Router use.

---

## Implementation notes (for implement step)

1. **Router:** Add optional dependency on LifecycleContextStore and a way to know which bot ids have handlesOwnedSpaces (e.g. Map<String, Boolean> or BotDefinition list). In route(Event): (a) compute filter-based bot ids as today; (b) for Discord events with channelId, get LifecycleContextStore.getByChannelId(channelId); if present, configuredBotId is owner; if owner has handlesOwnedSpaces, add owner to bot ids (single-owner: if lifecycle context exists for channel, prefer or only add owner so owned room wins). Dedupe so owner appears once. Apply to all Discord inbound event kinds (message, interaction for buttons, select menus, modal submits) — same channelId-based owner resolution.
2. **Config:** In ConfigLoader/BotConfig, add optional handlesOwnedSpaces (default false). In BotDefinition add boolean isHandlesOwnedSpaces() or similar. In bots.yaml for arrietty add handlesOwnedSpaces: true.
3. **Bootstrap/Engine:** When building Router, pass LifecycleContextStore and the map of botId -> handlesOwnedSpaces (from loaded BotDefinitions). Engine already has access to StateStore and lifecycle store; ensure Router is constructed with lifecycle store and bot flags.
4. **Arrietty workflow:** In workflows: define arrietty_room (or similar) with steps for status, retry, close, simple replies (e.g. prompt_for_field or done with message; optional call_action for status/retry/close if actions are added). In bots.yaml set arrietty.workflow.type: configured, params.workflowRef: arrietty_room. No Java code references "arrietty" by id except via config.
5. **Preserve:** OutboundDeliveryRouter unchanged. LifecycleContext and LifecycleContextStore unchanged (only read by Router for owner lookup). No hardcoded bot id "arrietty" in Java; only in config/bots.yaml and workflow bind lifecycleOwnerBotId: arrietty.
