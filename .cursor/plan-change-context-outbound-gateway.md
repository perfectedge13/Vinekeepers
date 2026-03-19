# Change context (for plan_change / implement)

## User request

Implement OutboundGateway Connector Boundary Refinement — clarify OutboundGateway as connector execution surface (Discord-shaped); document router vs gateway responsibility split; update Javadoc and specs; optional Bootstrap comment. **Docs/spec only, no API change.**

Previous step (discovery): connectors-registry, OutboundGateway, OutboundDeliveryRouter, Bootstrap; README; assets ASSET-OUTBOUND-GATEWAY, ASSET-OUTBOUND-DELIVERY-ROUTER.

---

## Scope

- **Features:** FEAT-CONNECTORS-DISCORD (connectors), FEAT-CORE (Bootstrap asset).
- **Requirements:** REQ-CONNECTORS-DISCORD-001, REQ-CORE-002.
- **Assets:** ASSET-OUTBOUND-GATEWAY, ASSET-OUTBOUND-DELIVERY-ROUTER, ASSET-BOOTSTRAP.
- **Specs:** specs/connectors-registry.yml, specs/core-registry.yml.
- **Docs:** README.md, mkdoc/features/domain/connectors/discord.md, mkdoc/features/domain/connectors/discord/decisions.md, mkdoc/features/domain/connectors/discord/contracts.md, mkdoc/features/domain/connectors/discord/how-it-works.md.
- **Code (Javadoc / optional comment only):** OutboundGateway.java, OutboundDeliveryRouter.java, Bootstrap.java.

---

## Per feature

### Feature: Discord event source and reply (FEAT-CONNECTORS-DISCORD)

- **id:** FEAT-CONNECTORS-DISCORD  
- **slug:** discord  
- **title:** Discord event source and reply  
- **status:** active  
- **doc_path:** features/domain/connectors/discord.md  
- **summary:** Discord event source and reply; a JDA-backed gateway receives live messages, preserves mention metadata for routing, and delivers workflow replies back to Discord.

### Requirement: REQ-CONNECTORS-DISCORD-001

- **id:** REQ-CONNECTORS-DISCORD-001  
- **title:** Discord event source and reply  
- **statement:** Discord event source and reply; generic outbound abstraction ReplySender (send) and OutboundGateway (send, getSelfUserId, createTextChannel, createThreadChannel, addPermissionOverride). DiscordReplySender extends ReplySender; DiscordGateway extends OutboundGateway. Per-bot identity via BotDefinition.getConnectorIdentity("discord"). ConnectorContext is generic (EventBus, OutboundDeliveryRouter only). Adapter registerBots() does per-bot gateway/sender/default registration only. Bootstrap registers engine reply sender, sink, and Discord ReplyTargetResolver by connector id. OutboundDeliveryRouter implements ReplySender; registerSender(botId, ReplySender, OutboundGateway); resolves sender from delivery target and lifecycle configuredBotId; getGatewayForChannel returns OutboundGateway or null when lifecycle bot has no gateway; getSelfUserIdForBot(botId) from that bot's gateway getSelfUserId. DiscordAppReplySink with lifecycle operations; fallback to text when intent not supported.  
- **Acceptance criteria (abbreviated):** DiscordEventSource exists and implements EventSource; per-bot Discord identity via BotDefinition.getConnectorIdentity("discord"); OutboundDeliveryRouter resolves sender from delivery target (LifecycleContextStore getByDeliveryTargetId → configuredBotId → that bot's sender; else channelId; absent context uses default sender); lifecycle: no silent fallback when configured bot has no sender; getGatewayForChannel returns null when lifecycle bot has no gateway; getSelfUserIdForBot from that bot's gateway; OutboundGateway exposes getSelfUserId, createTextChannel, createThreadChannel, addPermissionOverride; DiscordGateway extends OutboundGateway; optional outbound-only gateway for non-routed bots; DiscordAppReplySink uses OutboundDeliveryRouter; ReplySender/OutboundGateway generic; SpaceOperations createRoom/createThread typed results; fail-closed for missing connector.  
- **Traceability assets:** ASSET-REPLY-SENDER, ASSET-OUTBOUND-GATEWAY, ASSET-OUTBOUND-DELIVERY-ROUTER, ASSET-CONNECTOR-CONTEXT, ASSET-CONNECTOR-ADAPTER, ASSET-DISCORD-*, ASSET-SPACE-OPERATIONS*, ASSET-REPLY-TARGET-RESOLVER, ASSET-DISCORD-REPLY-TARGET-RESOLVER, ASSET-CREATE-ROOM-*, ASSET-CREATE-THREAD-*.  
- **Validation tests:** UNIT-ENGINE-DISCORD-REPLY, UNIT-DISCORD-APP-REPLY-SINK, UNIT-DISCORD-REPLY-TARGET-RESOLVER, UNIT-DISCORD-EVENT-SOURCE, UNIT-OUTBOUND-DELIVERY-ROUTER, UNIT-DISCORD-CONNECTOR-ADAPTER, UNIT-CREATE-CHANNEL-ACTION-DISCORD, UNIT-CREATE-CHANNEL-ACTION-LIFECYCLE-OWNER, UNIT-DISCORD-SPACE-OPERATIONS, MANUAL-CONNECTORS-DISCORD.  
- **anti_patterns:** (none in registry)

### Assets (in scope)

- **ASSET-OUTBOUND-GATEWAY**  
  - path: src/main/java/com/vinekeepers/connectors/OutboundGateway.java  
  - role: Generic gateway for outbound delivery (send, getSelfUserId, isConnected, createTextChannel, createThreadChannel, addPermissionOverride); connector gateways extend this.  
  - requires: REQ-CONNECTORS-DISCORD-001.  
  - **Refinement:** Clarify as **connector execution surface** — the interface through which a connector (e.g. Discord) performs outbound operations; current implementation is Discord-shaped (channel/thread/guild/permission semantics). Router vs gateway: gateway = per-connector execution; router = which bot/sender/gateway to use for a given delivery target.

- **ASSET-OUTBOUND-DELIVERY-ROUTER**  
  - path: src/main/java/com/vinekeepers/connectors/OutboundDeliveryRouter.java  
  - role: Implements ReplySender; routes outbound delivery; registerSender(botId, ReplySender, OutboundGateway); resolves sender from delivery target (LifecycleContextStore getByDeliveryTargetId when present, else channelId); lifecycle context yields configuredBotId then that bot's sender; getGatewayForChannel returns OutboundGateway; getSelfUserIdForBot(botId) from that bot's gateway; default sender for non-lifecycle; for lifecycle does not silently fall back.  
  - requires: REQ-CONNECTORS-DISCORD-001.  
  - **Refinement:** Document **router vs gateway responsibility split:** Router = routing (which bot/sender/gateway for a channel/target); Gateway = execution (send, createTextChannel, getSelfUserId, addPermissionOverride). Router holds botId → (ReplySender, OutboundGateway) and resolves by channel/lifecycle; it does not perform delivery itself except by delegating to the resolved ReplySender.

### Feature: Application bootstrap (FEAT-CORE) — Bootstrap asset only

- **ASSET-BOOTSTRAP**  
  - path: src/main/java/com/vinekeepers/core/Bootstrap.java  
  - role: Wire engine, config, ConnectorRegistry; build Discord adapter; call adapter.registerBots(bots, ConnectorContext); register engine reply sender, sink, ReplyTargetResolver by connector id; action and sink registration stay in Bootstrap.  
  - requires: REQ-CORE-002, REQ-TOOLS-001, REQ-LUNA-001.  
  - **Optional:** Add a short comment that engine/sink/reply-sender and router vs gateway wiring are Bootstrap’s responsibility (connector execution surface = gateway; delivery routing = OutboundDeliveryRouter).

---

## Doc excerpts

### mkdoc/features/domain/connectors/discord.md

- **Summary (excerpt):** Discord event source (REQ-CONNECTORS-DISCORD-001). Per-bot identity via getConnectorIdentity("discord"). ConnectorContext generic (EventBus, OutboundDeliveryRouter only). Core uses ReplySender, ReplyTargetResolver, OutboundGateway; Discord implements these. OutboundDeliveryRouter resolves sender from delivery target and lifecycle context; getSelfUserIdForBot(botId); no silent fallback for lifecycle. Key assets table includes ASSET-OUTBOUND-GATEWAY and ASSET-OUTBOUND-DELIVERY-ROUTER with current role text.

### mkdoc/features/domain/connectors/discord/decisions.md

- **2026-03-13 — Generic ReplySender and OutboundGateway:** Introduce ReplySender and OutboundGateway in connectors package. Core and engine use ReplySender; OutboundDeliveryRouter implements it and exposes getSelfUserIdForBot(botId). Discord implements both (DiscordReplySender, DiscordGateway). DiscordAppReplySink casts to DiscordGateway for interaction-specific methods. Consequence: Core stays connector-agnostic; lifecycle and workflow actions use router for sender resolution and getSelfUserIdForBot.

### mkdoc/features/domain/connectors/discord/contracts.md

- **OutboundGateway:** Generic gateway contract (transitional). Core uses this interface; DiscordGateway extends it for receive/send and interaction operations.  
- **OutboundDeliveryRouter:** Implements ReplySender. Resolves sender from delivery target and optional lifecycle context (by channelId → configuredBotId). getGatewayForChannel(channelId) returns gateway for that channel when lifecycle bot has one, or null when lifecycle bot has no gateway. getSelfUserIdForBot(botId) from that bot's gateway. For lifecycle channels, uses only the configured bot's sender; no silent fallback when that sender is unavailable.

### mkdoc/features/domain/connectors/discord/how-it-works.md

- **Reply path:** Reply delivery goes through OutboundDeliveryRouter. Router implements ReplySender; resolves which sender (gateway + token) to use. Non-lifecycle: default sender. Lifecycle: context → configuredBotId → that bot's sender only; no silent fallback. getGatewayForChannel / getSelfUserIdForBot described. Gateway contract exposes getSelfUserId and addPermissionOverride for workflow actions.

### mkdoc/features/domain/connectors/discord/known-issues.md

- Active: (None.) Resolved: (None.)

---

## Implementation constraints (docs/spec only, no API change)

- **No method signature or behavioral change** in OutboundGateway, OutboundDeliveryRouter, or Bootstrap.
- **Javadoc:** OutboundGateway — clarify that it is the connector execution surface for outbound operations (currently Discord-shaped: channel, thread, guild, permission). OutboundDeliveryRouter — clarify router vs gateway: router = which bot/sender/gateway for a delivery target; gateway = execution of send/createTextChannel/getSelfUserId/addPermissionOverride.
- **Specs:** connectors-registry.yml — update asset role text for ASSET-OUTBOUND-GATEWAY and ASSET-OUTBOUND-DELIVERY-ROUTER to reflect boundary (execution surface vs routing). core-registry.yml — optional: tighten ASSET-BOOTSTRAP role to mention router/gateway wiring.
- **Docs:** discord.md, discord/contracts.md, discord/decisions.md, discord/how-it-works.md — add or adjust wording for “OutboundGateway = connector execution surface (Discord-shaped)” and “router vs gateway responsibility split.” README — if the project layout or summary mentions OutboundGateway/OutboundDeliveryRouter, align with the same split.
- **Guardrails:** Do not delete requirements; do not add new spec keys; stay within existing schema. No removal or rename of files/assets.

---

## Schema constraints

- req-registry: requirement keys (id, title, statement, status, priority, type, behavior, acceptance, traceability, validation), asset keys (id, kind, path, role, requires, feature_ids). No new keys.
