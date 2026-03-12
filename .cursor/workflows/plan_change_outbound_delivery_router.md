# Change context (for plan_change / implement)

## Scope

**Request-derived:** Outbound delivery router — real configured bot identity for lifecycle rooms (e.g. Arrietty sends in lifecycle channel, Luna in intake); config-driven multi-bot connector identity (each configured bot can have its own Discord token/sender); outbound delivery router that resolves sender from delivery target and lifecycle context (configuredBotId), uses the correct bot's sender; for lifecycle rooms fail clearly if the correct bot's sender is unavailable (no silent fallback); one bot identity can send in many channels; connector setup is per-identity not 1:1 room.

**Impacted registry slice:** core-registry (engine, config, Luna/lifecycle), connectors-registry (Discord).

**Features:** FEAT-ENGINE, FEAT-CURSOR-GATHERING, FEAT-CONFIG, FEAT-CONNECTORS-DISCORD.

**Requirements:** REQ-CORE-003, REQ-CONFIG-001, REQ-LUNA-001, REQ-CONNECTORS-DISCORD-001.

**Assets:** ASSET-ENGINE, ASSET-APP-REPLY-SINK, ASSET-REPLY-TARGET, ASSET-BOOTSTRAP, ASSET-BOTS-YAML, ASSET-CONFIG-LOADER, ASSET-LIFECYCLE-CONTEXT, ASSET-POST-CHANNEL-MESSAGE-ACTION, ASSET-LAUNCH-CURSOR-RUN-ACTION, ASSET-CURSOR-RUN-MONITOR, ASSET-CREATE-CHANNEL-ACTION, ASSET-DISCORD-SOURCE, ASSET-DISCORD-REPLY, ASSET-DISCORD-GATEWAY, ASSET-DISCORD-REPLY-SINK.

---

## Per feature

### FEAT-ENGINE (engine)

**Feature:** Event-driven engine orchestration. Status: active. Doc: mkdoc/features/domain/core/engine.md. Summary: VinekeepersEngine routes events to matched bots, runs workflow runners, applies reasoner output, delivers replies via connector sink registry; build OutboundResponse; resolve ReplyTarget.

**Requirements:** REQ-CORE-003 — Event-driven engine routes events to bots; reply delivery uses connector contract (AppReplySink) with lifecycle operations; engine builds OutboundResponse, resolves ReplyTarget from event, calls sink registered for event source (transitional source-prefix). Criteria: Engine delivers via sink registry (source-prefix) and lifecycle methods; no defer in engine. Tests: UNIT-ENGINE (VinekeepersEngineTest). Anti_patterns: (none in registry for this req).

**Assets:** ASSET-ENGINE (src/main/java/.../VinekeepersEngine.java — route, run workflow/reasoner, deliver via sink registry; currently single sink per sourceId prefix, legacy setReplySender), ASSET-APP-REPLY-SINK, ASSET-REPLY-TARGET.

**Doc excerpts:**  
- **Decisions (engine):** Waiting-session routing for Discord; workflow runs before reasoner; proposed tool calls via ToolRunner.  
- **How it works:** Engine receives event → routing → workflow → reasoner → audit → replies delivered when source connector supports them.  
- **Contracts:** Engine coordinates bot execution; sink lifecycle (respondImmediately, sendFollowUp, updateMessage).  

**Implement note:** Delivery must switch from single sink/sender per source to router that resolves sender by (target + optional lifecycle configuredBotId); lifecycle delivery must fail clearly when that bot's sender is unavailable.

---

### FEAT-CURSOR-GATHERING (cursor-gathering)

**Feature:** Cursor-backed gathering workflow. Status: active. Doc: mkdoc/features/domain/workflow/cursor-gathering.md. Summary: Luna gathers repo/feature input, launch_cursor_run; Phase 1 lifecycle room with LifecycleContext (configuredBotId, runtimeBotInstanceId), create_channel, post_channel_message, provision_bot_instance, create_lifecycle_context, launch_cursor_run. Arrietty template for per-channel lifecycle instances.

**Requirements:** REQ-LUNA-001 — Luna luna_cursor workflow; Phase 1 lifecycle: LifecycleRunRecord, LifecycleContext (channelId, configuredBotId, runtimeBotInstanceId), RuntimeBotInstance; create_channel, post_channel_message, create_lifecycle_context, launch_cursor_run. Criteria: create_lifecycle_context bind precedence; post_channel_message interpolates lifecycleBotName from bind; launch_cursor_run ack to Discord with status and lifecycle room. Anti_patterns: Hardcoding Discord channel; storing secrets in state; do not rely on discordTrigger alone; do not log Cursor API key/body; do not assume single Cursor error shape; do not serialize null in Cursor payload.

**Assets:** ASSET-LIFECYCLE-CONTEXT (configuredBotId present), ASSET-POST-CHANNEL-MESSAGE-ACTION (needs correct sender by identity), ASSET-LAUNCH-CURSOR-RUN-ACTION (ack to Discord), ASSET-CURSOR-RUN-MONITOR (relay to Discord — needs correct sender), ASSET-CREATE-CHANNEL-ACTION (gateway may be per-identity).

**Doc excerpts:**  
- **Contracts (cursor-gathering):** LifecycleContext has channelId, configuredBotId, runtimeBotInstanceId; post_channel_message content from state+bind, lifecycleBotName in bind; CursorCloudRunMonitor relays to Discord.  
- **Known issues:** Flow depends on external Cursor/repo; documented path is Luna-specialized; run tracking in-memory.  
- **Decisions:** Luna config-driven in YAML; Arrietty template for lifecycle room instances.  

**Implement note:** Lifecycle room messages must be sent as the configured bot (e.g. Arrietty) using that bot's Discord sender; if that sender is missing, fail explicitly (no fallback to another bot). One bot identity can send in many channels.

---

### FEAT-CONFIG (config)

**Feature:** Bot config from YAML. Status: active. Doc: mkdoc/features/domain/config/config.md. Summary: ConfigLoader loads bot definitions; BotConfig; workflow refs, routing (discordMention, discordAuthors), runtime options.

**Requirements:** REQ-CONFIG-001 — Load bot definitions, routing, workflows from YAML; workflow block, bot runtime options, routing filter keys. Criteria: ConfigLoader parses workflow, routing, step bind (e.g. lifecycleBotName). Anti_patterns: (none).

**Assets:** ASSET-CONFIG-LOADER, ASSET-BOT-CONFIG, ASSET-BOTS-YAML. Bot definitions today have id, persona, workflow; no per-bot Discord token/connector identity yet — implement may add optional connector identity key (e.g. discordTokenEnvKey or connectorId) for multi-bot Discord.

**Doc excerpts:**  
- **Summary:** Each bot may specify workflow.type, workflowRef, discordMention, discordAuthors, conversationMode, sessionKeyStrategy; workflows section defines DSL.  

**Implement note:** Config-driven multi-bot connector identity implies bot config can specify which Discord token/env or connector identity to use so each bot can have its own sender; connector setup is per-identity not 1:1 room.

---

### FEAT-CONNECTORS-DISCORD (discord)

**Feature:** Discord event source and reply. Status: active. Doc: mkdoc/features/domain/connectors/discord.md. Summary: DiscordEventSource implements EventSource and DiscordReplySender; JDA gateway; DiscordAppReplySink implements AppReplySink; replies delivered via sink or legacy reply path.

**Requirements:** REQ-CONNECTORS-DISCORD-001 — DiscordEventSource, mention metadata, gateway createTextChannel; DiscordAppReplySink lifecycle ops; DiscordReplySender (channelId, messageId, content). Criteria: Replies from engine delivered to Discord via sink or legacy path; gateway createTextChannel for lifecycle room. Anti_patterns: (none in registry).

**Assets:** ASSET-DISCORD-SOURCE, ASSET-DISCORD-REPLY, ASSET-DISCORD-GATEWAY, ASSET-DISCORD-GATEWAY-CONTRACT, ASSET-DISCORD-REPLY-SINK. Today one DiscordEventSource per app; implement may introduce per-bot/identity connector instances (each with its own token and gateway/sender).

**Doc excerpts:**  
- **Contracts (discord):** EventSource.start(EventBus); DiscordReplySender send(channelId, messageId, content); DiscordGateway createTextChannel(guildId, channelName).  
- **Summary:** DiscordEventSource + DiscordReplySender; adapter delivers workflow replies and Cursor updates.  

**Implement note:** Multi-bot connector identity: each configured bot that sends to Discord can have its own token (e.g. from env key in config) and thus its own Discord gateway/sender; Bootstrap or a connector factory creates one connector (gateway + reply sink + reply sender) per identity; OutboundDeliveryRouter resolves sender by bot id (from event or from LifecycleContext.configuredBotId for lifecycle room).

---

## Schema constraints

- **req-registry:** requirement keys include id, title, statement, status, acceptance.criteria, traceability.assets, validation.tests; do not invent new top-level keys.
- **assets:** id, path, role, requires, feature_ids; optional anti_patterns. Stay within existing schema.

---

## Removal or rename

None. No file or asset deletion or rename requested.
