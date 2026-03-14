# Change context (for plan_change / implement)

**Plan:** Bot identity config generalization + ConnectorRegistry introduction (`.cursor/plans/bot-identity-connectorregistry-seam.plan.md`).

---

## Scope

**Request-derived:** ConnectorIdentity wrapper; BotDefinition connector-scoped identities (`getConnectorIdentity`); ConfigLoader dual-read (`identities.discord` + legacy); ConnectorRegistry; ConnectorAdapter; ConnectorContext (generic); DiscordConnectorConfig; DiscordConnectorAdapter; Bootstrap uses registry and adapter for per-bot Discord registration; action/sink registration stays in Bootstrap; config/bots.yaml migrated to `identities.discord`; docs/specs (handlesOwnedSpaces connector-scoped, defaultDiscordTokenEnvKey transitional).

**Impacted registry slice:** bot-registry, config-registry, core-registry, connectors-registry.

**Features / requirements / assets in scope:**
- **Bot:** REQ-BOT-001, REQ-BOT-003; ASSET-BOT-DEFINITION, ASSET-ROUTER, ASSET-ROUTING-FILTER; FEAT-BOT, FEAT-ROUTING.
- **Config:** REQ-CONFIG-001; ASSET-CONFIG-LOADER, ASSET-BOTS-YAML, ASSET-BOT-CONFIG, ASSET-BOT-DEFINITION; FEAT-CONFIG.
- **Core:** REQ-CORE-002, REQ-CORE-003; ASSET-BOOTSTRAP, ASSET-ENGINE, ASSET-APP-REPLY-SINK; FEAT-CORE, FEAT-ENGINE.
- **Connectors:** REQ-CONNECTORS-DISCORD-001; ASSET-DISCORD-SOURCE, ASSET-DISCORD-GATEWAY, ASSET-DISCORD-REPLY-SINK, ASSET-OUTBOUND-DELIVERY-ROUTER; FEAT-CONNECTORS-DISCORD.
- **New assets (to add):** ConnectorIdentity, ConnectorContext, ConnectorAdapter, ConnectorRegistry, DiscordConnectorConfig, DiscordConnectorAdapter (paths per plan).

---

## Per feature / requirement (excerpts)

### REQ-BOT-001 (Route events to bots by routing rules and ownership)
- **Statement:** Router matches events using RoutingRule/EventFilter; when Discord channel has lifecycle context and owner bot has handlesOwnedSpaces, single-owner precedence. Router depends on LifecycleContextStore and setHandlesOwnedSpacesByBotId(map).
- **Acceptance (key):** handlesMap from `bot.getConnectorIdentity("discord").map(d -> d.getAttribute("handlesOwnedSpaces")).orElse(false)` (or equivalent); Router receives map from Bootstrap.
- **Anti-patterns:** Do not hardcode bot ids; use config-driven handlesOwnedSpaces and lifecycle context.

### REQ-BOT-003 (Bot definition and persona/model)
- **Statement:** BotDefinition composes Persona, ModelProfile, workflowType/params, ToolPolicy, MemoryPolicy, optional discordTokenEnvKey, optional handlesOwnedSpaces; bots from YAML.
- **Target:** BotDefinition holds `Map<String, ConnectorIdentity> connectorIdentities`; access `Optional<ConnectorIdentity> getConnectorIdentity(String connectorId)`. No top-level Discord-named fields in preferred API. ConnectorIdentity: immutable; `getAttribute(key)` or `getAttributes()` so adapters read tokenEnvKey, handlesOwnedSpaces without core depending on connector names.
- **Anti-patterns:** (none listed in registry)

### REQ-CONFIG-001 (Load bot config from YAML)
- **Statement:** ConfigLoader loads bot definitions, routing, workflows; parses optional discordTokenEnvKey, handlesOwnedSpaces, discordTrigger, discordMention, discordAuthors.
- **Target:** Dual-read: parse `identities.discord` (tokenEnvKey, handlesOwnedSpaces) and legacy top-level keys; normalize into ConnectorIdentity per connector; new shape preferred; new wins if both present. BotConfig retains **defaultDiscordTokenEnvKey** as retained transitional root-level (document as such).
- **Acceptance (key):** Parse identities.discord and legacy; build ConnectorIdentity per connector; pass to BotDefinition.

### REQ-CORE-002 (Application bootstrap and entrypoint)
- **Statement:** Bootstrap wires engine, config, connectors, shared tools.
- **Target:** Bootstrap creates ConnectorRegistry; builds DiscordConnectorConfig from config (defaultTokenEnvKey); creates Discord adapter with that config; registers adapter under "discord"; builds handlesMap from getConnectorIdentity("discord"); calls adapter.registerBots(bots, context). **Action/sink registration stays in Bootstrap** (engine.setReplySender, engine.registerSink, cursorCloudRunMonitor.setReplySender, actionRegistry.register create_channel/create_thread).

### REQ-CORE-003 (Event-driven engine)
- No structural change to engine; reply delivery and sink registry unchanged.

### REQ-CONNECTORS-DISCORD-001 (Discord event source and reply)
- **Target:** Per-bot identity via BotDefinition.getConnectorIdentity("discord") (tokenEnvKey, handlesOwnedSpaces). ConnectorContext is generic (Env, EventBus, OutboundDeliveryRouter only; no defaultDiscordTokenEnvKey). Discord-specific options in DiscordConnectorConfig passed to Discord adapter constructor. Adapter implements ConnectorAdapter; registerBots() does per-bot gateway/sender/default registration only; Bootstrap keeps sink/action wiring.

---

## Anti_patterns (guardrails + registries)

- **Guardrails:** Do not delete requirements; do not remove required functionality; do not change schema or invent new spec keys; repair spec drift before coding; consider anti_patterns on requirements/assets; feature slugs are not bot ids.
- **REQ-BOT-001:** Do not hardcode bot ids; use config-driven handlesOwnedSpaces and lifecycle context.

---

## Schema constraints

- Specs use req-registry schema: requirements (id, title, statement, acceptance.criteria, traceability.assets, validation.tests), assets (id, path, role, requires). No new keys; stay within existing schema. Update asset roles and requirement statements to reflect ConnectorIdentity, getConnectorIdentity, ConnectorRegistry, ConnectorAdapter, ConnectorContext, DiscordConnectorConfig, DiscordConnectorAdapter.

---

## Doc excerpts (mkdoc)

- **docs_dir:** mkdoc (from .cursor/project.yml).
- **features/domain/bot/bot.md:** BotDefinition composes persona, model, workflow, tools, runtime; routing in routing feature. Sub-pages: how-it-works, change-log, known-issues, decisions, contracts, tests.
- **features/domain/config/config.md:** ConfigLoader loads from YAML; workflow.type/params, discordTrigger, discordMention, discordAuthors, conversationMode, sessionKeyStrategy. Assets: ConfigLoader, BotConfig, bots.yaml.
- **features/domain/connectors/discord.md:** OutboundDeliveryRouter resolves sender from delivery target and lifecycle context; getGatewayForChannel, getDiscordUserIdForBot; no silent fallback for lifecycle channels. **Contracts:** EventSource.start(EventBus); DiscordReplySender; OutboundDeliveryRouter implements DiscordReplySender; registers senders per bot when discordTokenEnvKey set. **Decisions:** Preserve Discord mentions in connector events; reply delivery through connector abstraction.
- **features/domain/core/core.md:** Bootstrap wires event bus, router, state store, connectors, configured bot runners.
- **Planned doc updates:** handlesOwnedSpaces documented as **connector-scoped ownership for Discord in this pass**, not generic bot-wide. defaultDiscordTokenEnvKey as **retained transitional root-level setting**, not long-term identity model. ConnectorRegistry/adapter: action and sink registration stay in Bootstrap; Discord adapter only per-bot gateway/sender/default registration.

---

## Plan file reference (summary)

- **ConnectorIdentity:** Immutable; getAttribute(key) / getAttributes(); built from raw map in ConfigLoader.
- **BotDefinition:** connectorIdentities map; getConnectorIdentity(connectorId); legacy fields normalized from YAML, not stored at top level in preferred shape.
- **ConfigLoader:** Parse identities.discord and legacy; build ConnectorIdentity per connector; BotConfig keeps defaultDiscordTokenEnvKey (transitional).
- **ConnectorContext:** Env, EventBus, OutboundDeliveryRouter only; no Discord-named fields.
- **ConnectorAdapter:** void registerBots(List<BotDefinition> bots, ConnectorContext context).
- **ConnectorRegistry:** register(id, adapter), get(id).
- **DiscordConnectorConfig:** defaultTokenEnvKey (from config defaultDiscordTokenEnvKey).
- **DiscordConnectorAdapter:** Implements ConnectorAdapter; constructor takes DiscordConnectorConfig; registerBots does per-bot gateway/sender/default only.
- **Bootstrap:** Build registry; build DiscordConnectorConfig; create and register Discord adapter; build handlesMap from getConnectorIdentity("discord"); call adapter.registerBots(bots, context); keep engine/sink/cursor/action registration in Bootstrap.
- **config/bots.yaml:** Migrate to identities.discord (tokenEnvKey, handlesOwnedSpaces); retain defaultDiscordTokenEnvKey at root as transitional.
