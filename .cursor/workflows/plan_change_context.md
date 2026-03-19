# Change context (for plan_change / implement)

## Scope

**Request:** Implement outbound delivery refactor — connector-keyed reply sender in VinekeepersEngine (Map by connector id, setReplySender(connectorId, sender)), remove hard-coded "discord" from engine fallback path, Bootstrap registers engine.setReplySender("discord", outboundDeliveryRouter). Preserve all Discord and lifecycle-thread behavior.

**Impacted registry slice:** core (engine, bootstrap), connectors (reply sender, outbound delivery).

**Features:** FEAT-ENGINE, FEAT-CORE, FEAT-CONNECTORS-DISCORD.

**Requirements:** REQ-CORE-002, REQ-CORE-003, REQ-CONNECTORS-DISCORD-001.

**Assets:** ASSET-ENGINE, ASSET-BOOTSTRAP, ASSET-REPLY-SENDER, ASSET-OUTBOUND-DELIVERY-ROUTER, ASSET-DISCORD-REPLY-SINK.

---

## Per feature

### FEAT-ENGINE (engine)

- **Feature:** Event-driven engine orchestration; doc_path: features/domain/core/engine.md; status: active.
- **Summary:** VinekeepersEngine routes events to bots, runs workflow and reasoner, applies tool/state side effects, delivers replies via connector sink registry (lifecycle: respondImmediately, sendFollowUp, updateMessage); no defer in engine.
- **REQ-CORE-003:** Event-driven engine routes events to bots. Reply delivery uses ReplySender (e.g. OutboundDeliveryRouter) and connector contract (AppReplySink) with lifecycle operations; for Discord, OutboundDeliveryRouter resolves sender from ReplyTarget and lifecycle configuredBotId—no silent fallback when lifecycle channel's bot has no registered sender. Engine builds OutboundResponse, resolves ReplyTarget, calls sink registered for event source.
- **Criteria (short):** Engine subscribes to bus, dispatches to bots; Discord message events add bots with WAITING_INPUT for session key; uses registered runners and reasoner; delivers via sink registry and lifecycle methods; for Discord, OutboundDeliveryRouter resolves sender by target and configuredBotId, no silent fallback for lifecycle; no bot registered logs warning only.
- **Traceability assets:** ASSET-ENGINE, ASSET-WORKFLOW-RUNNER, ASSET-TOOL-RUNNER, ASSET-REASONER-INPUT, ASSET-REASONER-OUTPUT, ASSET-OUTBOUND-RESPONSE, ASSET-APP-REPLY-SINK, ASSET-REPLY-TARGET, ASSET-CAPABILITIES, ASSET-RESPONSE-INTENT, ASSET-REPLY-SENDER.
- **Validation tests:** UNIT-ENGINE (VinekeepersEngineTest).
- **ASSET-ENGINE:** path: src/main/java/com/vinekeepers/core/VinekeepersEngine.java; role: Route events to bots; run workflow and reasoner; deliver replies via ReplySender and connector sink registry; build OutboundResponse; resolve ReplyTarget; no defer in engine.
- **Doc excerpts — decisions:** Waiting-session routing for Discord; workflow before reasoner; ToolRunner for proposed tool calls; engine orchestration separate from bootstrap.
- **Doc excerpts — contracts:** Engine internal; ReasonerInput/ReasonerOutput/WorkflowRunResult schemas; VinekeepersEngine, WorkflowRunner, ToolRunner, Reasoner interfaces.
- **Doc excerpts — known-issues:** Missing bot logged and skipped; reply delivery connector-specific; built-in reply path documented for Discord.

### FEAT-CORE (bootstrap)

- **Feature:** Specs governance and bootstrap; doc_path: features/domain/core/core.md; status: active.
- **REQ-CORE-002:** Application bootstrap. VinekeepersApp loads .env and bootstraps engine, config, connectors. Bootstrap wires engine, config loader, ConnectorRegistry; creates Discord adapter; calls adapter.registerBots(bots, context); engine/sink/cursor/action registration remain in Bootstrap.
- **Criteria (short):** Main runs Bootstrap; Bootstrap wires engine, config, ConnectorRegistry; creates WorkflowRunner per bot and registerRunner(botId, runner); registers shared tools and ToolRunner in workflows.
- **ASSET-BOOTSTRAP:** path: src/main/java/com/vinekeepers/core/Bootstrap.java; role: Wire engine, config, ConnectorRegistry; build Discord adapter; call adapter.registerBots(bots, ConnectorContext); build handlesMap from getConnectorIdentity("discord"); action and sink registration stay in Bootstrap.

### FEAT-CONNECTORS-DISCORD (discord)

- **Feature:** Discord event source and reply; doc_path: features/domain/connectors/discord.md; status: active.
- **REQ-CONNECTORS-DISCORD-001:** Discord event source and reply; ReplySender and OutboundGateway; per-bot identity via getConnectorIdentity("discord"); ConnectorContext generic (EventBus, OutboundDeliveryRouter); adapter registerBots() per-bot only; Bootstrap keeps sink and action registration. OutboundDeliveryRouter implements ReplySender; registerSender(botId, ReplySender, OutboundGateway); resolves sender from delivery target and lifecycle configuredBotId.
- **Criteria (short):** DiscordEventSource exists, emits events with mention metadata; per-bot identity via getConnectorIdentity("discord"); OutboundDeliveryRouter resolves sender from delivery target (LifecycleContextStore) or channelId; lifecycle: use configured bot's sender only, no silent fallback; getGatewayForChannel null when lifecycle bot has no gateway; getSelfUserIdForBot from gateway; DiscordAppReplySink lifecycle operations; ReplySender contract; engine delivers via sink or legacy reply when source is Discord.
- **ASSET-REPLY-SENDER:** path: src/main/java/com/vinekeepers/connectors/ReplySender.java; role: Generic contract send(channelId, messageId, content); connector senders implement for engine and workflow actions.
- **ASSET-OUTBOUND-DELIVERY-ROUTER:** path: src/main/java/com/vinekeepers/connectors/OutboundDeliveryRouter.java; role: Implements ReplySender; registerSender(botId, ReplySender, OutboundGateway); resolves sender from delivery target and lifecycle context; getGatewayForChannel; getSelfUserIdForBot; no silent fallback for lifecycle.
- **ASSET-DISCORD-REPLY-SINK:** path: src/main/java/com/vinekeepers/connectors/DiscordAppReplySink.java; role: Implements AppReplySink; uses OutboundDeliveryRouter for sender resolution; lifecycle operations; adapter owns timing/defer.
- **Doc excerpts — decisions:** Generic ReplySender and OutboundGateway; core and engine use ReplySender; OutboundDeliveryRouter implements it; Discord implements both; DiscordAppReplySink casts to DiscordGateway for interaction methods. Preserve Discord mentions in payload.
- **Doc excerpts — contracts:** ConnectorRegistry by connectorId; ConnectorAdapter registerBots(bots, context); ConnectorContext EventBus, OutboundDeliveryRouter only; ReplySender send(channelId, messageId, content); OutboundDeliveryRouter resolves sender by target and lifecycle; getGatewayForChannel returns null when lifecycle bot has no gateway; no silent fallback for lifecycle.

---

## Implementation constraints (from discovery)

- **Current state:** Engine has single `ReplySender replySender` and `setReplySender(ReplySender)`. In `deliverReply`, when no sink is found, fallback uses `event.getSourceId().startsWith("discord:")` and `replySender.send(...)`.
- **Target state:** Engine holds `Map<String, ReplySender> replySenders` keyed by connector id; `setReplySender(String connectorId, ReplySender sender)`; fallback path uses connector id derived from event source (e.g. prefix before ":") to look up sender from map—no "discord" literal. Bootstrap calls `engine.setReplySender("discord", outboundDeliveryRouter)` (and keeps `engine.registerSink("discord", new DiscordAppReplySink(outboundDeliveryRouter))`).
- **Preserve:** All Discord behavior (sink path unchanged); lifecycle-thread behavior (OutboundDeliveryRouter resolution unchanged); CursorCloudRunMonitor and other consumers that need a ReplySender continue to receive the same router (Bootstrap already passes outboundDeliveryRouter to engine and monitor).

---

## Schema constraints

- req-registry: requirement keys id, title, statement, status, priority, type, behavior, acceptance, traceability, validation; asset keys id, kind, path, role, requires, feature_ids. Do not add new spec keys or delete active requirements.
